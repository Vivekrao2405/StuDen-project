#!/bin/sh
# Entrypoint for the studen-code-runner image. Invoked by DockerCodeExecutionService as the
# container's Cmd: `run.sh compile <language>` or `run.sh run <language>`. Never uses `set -e` --
# every branch below deliberately captures and records the real exit code instead of letting the
# shell bail out on a student program's non-zero exit.
#
# Bind mounts (fixed, in-container paths only -- never a host path, so nothing here can leak
# server filesystem details through a compiler/runtime error message):
#   /workspace   read-write during compile, read-only during run. Contains src/ (source) and,
#                after a successful compile, bin/ (compiled artifact) and output/ (compile result).
#   /run         read-write, fresh per test case. Contains input/stdin.txt and, after running,
#                output/{stdout,stderr,exit_code}.

MODE="$1"
LANG="$2"

case "$MODE" in
  compile)
    mkdir -p /workspace/bin /workspace/output
    case "$LANG" in
      java)
        javac -d /workspace/bin /workspace/src/Main.java 2> /workspace/output/compile_stderr
        echo $? > /workspace/output/compile_exit
        ;;
      c)
        gcc -O2 -std=c11 -o /workspace/bin/main /workspace/src/main.c 2> /workspace/output/compile_stderr
        echo $? > /workspace/output/compile_exit
        ;;
      cpp)
        g++ -O2 -std=c++17 -o /workspace/bin/main /workspace/src/main.cpp 2> /workspace/output/compile_stderr
        echo $? > /workspace/output/compile_exit
        ;;
      python)
        # No compile step -- a syntax check still gives fast, honest COMPILATION_ERROR feedback
        # instead of only discovering a SyntaxError on the first test-case run.
        python3 -m py_compile /workspace/src/main.py 2> /workspace/output/compile_stderr
        echo $? > /workspace/output/compile_exit
        ;;
      *)
        echo "Unsupported language: $LANG" > /workspace/output/compile_stderr
        echo 1 > /workspace/output/compile_exit
        ;;
    esac
    ;;
  run)
    mkdir -p /run/output
    case "$LANG" in
      java)
        ( cd /workspace/bin && java -XX:+UseSerialGC -Xmx200m Main < /run/input/stdin.txt > /run/output/stdout 2> /run/output/stderr )
        echo $? > /run/output/exit_code
        ;;
      python)
        python3 /workspace/src/main.py < /run/input/stdin.txt > /run/output/stdout 2> /run/output/stderr
        echo $? > /run/output/exit_code
        ;;
      c|cpp)
        /workspace/bin/main < /run/input/stdin.txt > /run/output/stdout 2> /run/output/stderr
        echo $? > /run/output/exit_code
        ;;
      *)
        echo "Unsupported language: $LANG" > /run/output/stderr
        : > /run/output/stdout
        echo 1 > /run/output/exit_code
        ;;
    esac
    ;;
  *)
    echo "Unknown mode: $MODE" >&2
    exit 1
    ;;
esac
