import QRCodeLib from "qrcode";
import { useEffect, useState } from "react";

interface QRCodeProps {
  value: string;
  size?: number;
  className?: string;
}

/**
 * Renders a QR code generated entirely client-side from the given value (the actual public
 * profile URL) — no external QR-generation API, no data ever leaves the browser.
 */
export function QRCode({ value, size = 120, className }: QRCodeProps) {
  const [dataUrl, setDataUrl] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    QRCodeLib.toDataURL(value, {
      width: size,
      margin: 1,
      color: { dark: "#0f172a", light: "#ffffff" },
    })
      .then((url) => {
        if (!cancelled) setDataUrl(url);
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
