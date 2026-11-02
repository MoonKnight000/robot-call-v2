-- Whether the early reply's model also judges when the caller has finished (AiAgentCallBehaviour.llmTurnDetection).
-- NULL = the deployment default (voice-agent.dialog.speculative-turn-cue).
ALTER TABLE ai_agent ADD COLUMN llm_turn_detection BOOLEAN;
