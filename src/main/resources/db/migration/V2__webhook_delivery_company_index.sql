-- The panel's delivery log pages one company's rows newest first.
CREATE INDEX idx_webhook_delivery_company_created ON webhook_delivery (company_id, created_at DESC);
