#!/bin/bash
# Manual production smoke test for the execution-server HTTP API: waits for /health, then runs a
# real /compile + /run round trip for each supported language through the actual endpoints (not
# unit tests). Requires jq. Not wired into CI -- run by hand after a deploy.
set -uo pipefail
cd "$(dirname "$0")/.."

API_KEY=$(grep '^EXECUTION_API_KEY=' .env | cut -d= -f2)
BASE=http://localhost:${SERVER_PORT:-8081}

echo "waiting for /health..."
for i in $(seq 1 30); do
  curl -sf "$BASE/health" >/dev/null 2>&1 && break
  sleep 1
done
body=$(curl -sf "$BASE/health") && echo "/health: PASS  $body" || echo "/health: FAIL -- request failed"

pass() { echo "$1: PASS"; }
fail() { echo "$1: FAIL -- $2"; }

check_lang() {
  local name="$1" lang="$2" src="$3" id="smoke-$2-$RANDOM"
  local compile_json run_json status stdout stderr

  compile_json=$(curl -s -X POST "$BASE/compile" -H "Content-Type: application/json" \
    -H "X-Execution-Api-Key: $API_KEY" \
    -d "$(jq -n --arg id "$id" --arg lang "$lang" --arg src "$src" \
      '{executionId:$id,language:$lang,sourceCode:$src,timeoutSeconds:30,memoryLimitMb:256,cpuLimit:1.0,pidsLimit:64,outputLimitKb:1024}')")

  if [ "$(echo "$compile_json" | jq -r '.success')" != "true" ]; then
    fail "$name" "compile failed: $compile_json"
    return
  fi

  run_json=$(curl -s -X POST "$BASE/run" -H "Content-Type: application/json" \
    -H "X-Execution-Api-Key: $API_KEY" \
    -d "$(jq -n --arg id "$id" '{executionId:$id,stdin:"",timeoutSeconds:30,memoryLimitMb:256,cpuLimit:1.0,pidsLimit:64,outputLimitKb:1024}')")

  status=$(echo "$run_json" | jq -r '.status')
  stdout=$(echo "$run_json" | jq -r '.stdout')
  stderr=$(echo "$run_json" | jq -r '.stderr')

  if [ "$status" = "SUCCESS" ] && echo "$stdout" | grep -q "STUDEN WORKS" && [ -z "$stderr" ]; then
    pass "$name"
  else
    fail "$name" "run_json=$run_json"
  fi
}

check_lang "Python" "PYTHON" 'print("STUDEN WORKS")'
check_lang "Java" "JAVA" 'public class Main { public static void main(String[] args) { System.out.println("STUDEN WORKS"); } }'
check_lang "C" "C" '#include <stdio.h>
int main() { printf("STUDEN WORKS\n"); return 0; }'
check_lang "C++" "CPP" '#include <iostream>
int main() { std::cout << "STUDEN WORKS" << std::endl; return 0; }'
