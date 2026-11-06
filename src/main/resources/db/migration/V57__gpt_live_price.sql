-- OpenAI GPT-Live (gpt-live-1): the voice engine is billed per minute ($0.05/min, per second),
-- its delegation model per token at that model's own row (or the common LLM rate).
-- 640 UZS ≈ $0.05 at ~12 800 UZS/$; platform admins correct it through /api/platform/model-prices.
INSERT INTO billing_model_price (model, input_per_million_uzs, output_per_million_uzs, cached_per_million_uzs,
                                 realtime_per_minute_uzs, note)
VALUES ('gpt-live-1', 0, 0, 0, 640, 'OpenAI GPT-Live — $0.05/daqiqa; delegatsiya modeli tokenlari alohida')
ON CONFLICT (model) DO NOTHING;
