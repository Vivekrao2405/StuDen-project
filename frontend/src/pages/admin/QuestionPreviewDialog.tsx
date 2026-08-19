import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { QuestionContent } from "@/components/shared/QuestionContent";
import type { Difficulty } from "@/lib/api/types";
import { isPlainTextContent } from "@/lib/questionContent";
import { difficultyBadgeVariant, difficultyLabel } from "@/pages/admin/questionBankOptions";

interface PreviewOption {
  id: string;
  optionText: string;
  isCorrect: boolean;
}

interface QuestionPreviewDialogProps {
  open: boolean;
  onClose: () => void;
  questionText: string;
  skillName: string;
  difficulty: Difficulty;
  options: PreviewOption[];
}

// Shows the question exactly as a learner would eventually see it (spec §41) — no correct-answer
// styling by default — with a separate admin-only toggle to reveal it. This is a UI-only preview
// built from in-progress form state; it never calls the learner API (which only ever returns
// PUBLISHED questions).
export function QuestionPreviewDialog({ open, onClose, questionText, skillName, difficulty, options }: QuestionPreviewDialogProps) {
  const [revealAnswer, setRevealAnswer] = useState(false);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setRevealAnswer(false);
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Preview</DialogTitle>
          <DialogDescription>What a learner will see once this question is published.</DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="flex items-center gap-2">
            {skillName ? <Badge variant="outline">{skillName}</Badge> : null}
            <Badge variant={difficultyBadgeVariant(difficulty)}>{difficultyLabel(difficulty)}</Badge>
          </div>

          {questionText && !isPlainTextContent(questionText) ? (
            <QuestionContent text={questionText} textClassName="text-sm font-medium text-foreground" />
          ) : (
            <p className="text-sm font-medium text-foreground">{questionText || "Your question text will appear here."}</p>
          )}

          <ul className="space-y-1.5">
            {options.map((option, i) => (
              <li
                key={option.id}
                className={`rounded-lg border px-3 py-2 text-sm ${
                  revealAnswer && option.isCorrect ? "border-primary bg-accent text-foreground" : "border-border text-foreground"
                }`}
              >
                {option.optionText && !isPlainTextContent(option.optionText) ? (
                  <div className="flex gap-2">
                    <span className="shrink-0 text-muted-foreground">{String.fromCharCode(65 + i)}.</span>
                    <QuestionContent text={option.optionText} className="min-w-0 flex-1" />
                  </div>
                ) : (
                  <>
                    <span className="mr-2 text-muted-foreground">{String.fromCharCode(65 + i)}.</span>
                    {option.optionText || <span className="text-muted-foreground">Empty option</span>}
                  </>
                )}
              </li>
            ))}
          </ul>
        </div>

        <div className="flex items-center justify-between border-t border-border pt-4">
          <Button type="button" variant="outline" size="sm" onClick={() => setRevealAnswer((v) => !v)}>
            {revealAnswer ? "Hide answer" : "Reveal answer (admin only)"}
          </Button>
          <Button type="button" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
