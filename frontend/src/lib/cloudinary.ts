/**
 * Requests a smaller, auto-format/auto-quality Cloudinary derivative for display contexts that
 * don't need the full-resolution original (cards, grid thumbnails, edit-form previews).
 * Cloudinary generates these on demand from the untouched original via URL transformation
 * params — nothing is re-uploaded or modified, so the stored asset itself never loses quality.
 *
 * `c_limit` only ever scales an image down to fit `width`, never up and never crops, so this is
 * safe to apply to any Cloudinary delivery URL regardless of the original's aspect ratio.
 */
export function cloudinaryThumb(url: string | null | undefined, width: number): string | null {
  if (!url) return null;
  const marker = "/upload/";
  const index = url.indexOf(marker);
  if (index === -1) return url; // not a Cloudinary delivery URL (e.g. a local blob: preview) — pass through
  const insertAt = index + marker.length;
  return `${url.slice(0, insertAt)}w_${width},c_limit,q_auto,f_auto/${url.slice(insertAt)}`;
}
