CREATE TABLE post_images (
    id UUID PRIMARY KEY,
    post_id UUID,
    uploader_id UUID NOT NULL,
    url VARCHAR(1000),
    public_id VARCHAR(255),
    status VARCHAR(50),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_images_uploader FOREIGN KEY (uploader_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_post_images_publicId ON post_images (public_id);
CREATE INDEX idx_post_images_post_id ON post_images (post_id);
CREATE INDEX idx_post_images_uploader_id ON post_images (uploader_id);

CREATE TRIGGER trg_post_images_updated_at
BEFORE UPDATE ON post_images FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();