import { Plus } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/ApiError";
import { createTopic, listTopics } from "@/lib/api/endpoints/questionBank";
import type { TopicResponse } from "@/lib/api/types";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";

interface QuestionTopicPickerProps {
  skillId: string | null;
  value: string | null;
  onChange: (topicId: string | null) => void;
  disabled?: boolean;
}

// Topics are always scoped to a skill (spec §11) — reloads whenever skillId changes, and clears
// the current selection if it switches to a different skill. Topic creation is inline here
// (matching SkillPicker's "add custom skill" UX) rather than a separate admin page — spec §15
// deliberately keeps topic management minimal for Phase 7.1.
export function QuestionTopicPicker({ skillId, value, onChange, disabled }: QuestionTopicPickerProps) {
  const [topics, setTopics] = useState<TopicResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [adding, setAdding] = useState(false);
  const [newTopicName, setNewTopicName] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!skillId) {
      setTopics([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    listTopics(skillId)
      .then((result) => {
        if (!cancelled) setTopics(result);
      })
      .catch(() => {
        if (!cancelled) setTopics([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [skillId]);

  async function handleCreate() {
    if (!skillId || !newTopicName.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const topic = await createTopic(skillId, newTopicName.trim());
      setTopics((prev) => (prev.some((t) => t.id === topic.id) ? prev : [...prev, topic]));
      onChange(topic.id);
      setAdding(false);
      setNewTopicName("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't add that topic. Please try again.");
    } finally {
      setCreating(false);
    }
  }

  if (!skillId) {
    return <p className="text-sm text-muted-foreground">Select a skill first.</p>;
  }

  if (adding) {
    return (
      <div className="space-y-2">
        <div className="flex gap-2">
          <Input
            value={newTopicName}
            onChange={(e) => setNewTopicName(e.target.value)}
            placeholder="e.g. Functions"
            className="h-10"
            autoFocus
            disabled={creating}
          />
          <Button type="button" size="sm" onClick={handleCreate} disabled={creating || !newTopicName.trim()}>
            {creating ? "Adding..." : "Add"}
          </Button>
          <Button type="button" size="sm" variant="outline" onClick={() => setAdding(false)} disabled={creating}>
            Cancel
          </Button>
        </div>
        {error ? <p className="text-xs text-destructive">{error}</p> : null}
      </div>
    );
  }

  return (
    <div className="flex gap-2">
      <select
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value || null)}
        className={QB_SELECT_CLASS}
        disabled={disabled || loading}
      >
        <option value="">No topic</option>
        {topics.map((topic) => (
          <option key={topic.id} value={topic.id}>
            {topic.name}
          </option>
        ))}
      </select>
      <Button type="button" variant="outline" size="sm" onClick={() => setAdding(true)} disabled={disabled}>
        <Plus className="size-3.5" /> New
      </Button>
    </div>
  );
}
