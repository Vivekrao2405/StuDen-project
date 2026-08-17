import { useEffect, useState } from "react";

import { cn } from "@/lib/utils";

interface CircularScoreProps {
  value: number;
  max?: number;
  size?: number;
  strokeWidth?: number;
  className?: string;
  trackClassName?: string;
}

/**
 * Small SVG ring used for the landing page's illustrative score visuals (hero card, placement
 * readiness section, skill identity card). Animates from 0 to `value` once on mount — skipped
 * entirely for users who prefer reduced motion, in which case it renders at its final position
 * immediately.
 */
export function CircularScore({
  value,
  max = 100,
  size = 96,
  strokeWidth = 8,
  className,
  trackClassName,
}: CircularScoreProps) {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const target = circumference * (1 - Math.min(Math.max(value, 0), max) / max);

  const [offset, setOffset] = useState(() =>
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? target
      : circumference
  );

  useEffect(() => {
    const frame = requestAnimationFrame(() => setOffset(target));
    return () => cancelAnimationFrame(frame);
    // Re-run only when the target position itself changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        strokeWidth={strokeWidth}
        className={cn("stroke-muted", trackClassName)}
      />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        strokeWidth={strokeWidth}
        strokeDasharray={circumference}
        strokeDashoffset={offset}
        strokeLinecap="round"
        className={cn("stroke-primary transition-[stroke-dashoffset] duration-1000 ease-out", className)}
      />
    </svg>
  );
}
