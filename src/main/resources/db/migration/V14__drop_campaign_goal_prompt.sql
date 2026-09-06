-- campaign.goal_prompt is dead weight. Its name promised that it reached the model — it was
-- described as "LLM system prompt qismi" — but nothing has ever read it: the prompt is built
-- from the scenario's rolePrompt, its stages and its guardrails, and the only place this
-- column appeared was its own CRUD. A required text field that looks like it steers the
-- conversation and does not is worse than no field, because somebody writes instructions
-- into it and believes the agent is following them.
--
-- What it was meant for now has real homes: the script is the scenario's rolePrompt, a
-- per-campaign variation is the A/B variant's promptOverride, and a plain note about a
-- round of calling is the campaign's name.

ALTER TABLE campaign DROP COLUMN goal_prompt;
