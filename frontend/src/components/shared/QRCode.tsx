import QRCodeLib from "qrcode";
import { useEffect, useState } from "react";

import logoMark from "@/assets/logo-mark.png";

interface QRCodeProps {
  value: string;
  size?: number;
  className?: string;
}

/**
 * Renders a QR code generated entirely client-side from the given value (the actual public
 * profile URL) — no external QR-generation API, no data ever leaves the browser. Draws the
 * StuDen mark in the center on a white backdrop; errorCorrectionLevel "H" (~30% recoverable)
 * keeps the code scannable despite the logo covering part of it.
 */
export function QRCode({ value, size = 120, className }: QRCodeProps) {
  const [dataUrl, setDataUrl] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const canvas = document.createElement("canvas");

    QRCodeLib.toCanvas(canvas, value, {
      width: size,
      margin: 1,
      errorCorrectionLevel: "H",
      color: { dark: "#0f172a", light: "#ffffff" },
    })
      .then(() => {
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        const finish = () => {
          if (!cancelled) setDataUrl(canvas.toDataURL());
        };

        const logo = new Image();
        logo.onload = () => {
          if (cancelled) return;
          const logoSize = size * 0.22;
          const center = size / 2;
          const backdropRadius = logoSize / 2 + logoSize * 0.18;
          ctx.fillStyle = "#ffffff";
          ctx.beginPath();
          ctx.arc(center, center, backdropRadius, 0, Math.PI * 2);
          ctx.fill();
          ctx.drawImage(logo, center - logoSize / 2, center - logoSize / 2, logoSize, logoSize);
          finish();
        };
        logo.onerror = finish;
        logo.src = logoMark;
      })
      .catch(() => {
        if (!cancelled) setDataUrl(null);
      });

    return () => {
      cancelled = true;
    };
  }, [value, size]);

  if (!dataUrl) {
    return <div className={className} style={{ width: size, height: size }} aria-hidden="true" />;
  }

  return (
    <img
      src={dataUrl}
      alt={`QR code linking to ${value}`}
      width={size}
      height={size}
      className={className}
    />
  );
}
