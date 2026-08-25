-- Preserves the original upload's MIME type and filename so the resource file-serving endpoint
-- (ResourceController#viewFile/#downloadFile) can return correct Content-Type/Content-Disposition
-- headers instead of relying on Cloudinary's raw-delivery content sniffing (which serves
-- extension-less "raw" public IDs as application/octet-stream, causing browsers to force-download
-- PDFs instead of rendering them).
ALTER TABLE resources ADD COLUMN file_content_type VARCHAR(255);
ALTER TABLE resources ADD COLUMN file_name VARCHAR(255);
