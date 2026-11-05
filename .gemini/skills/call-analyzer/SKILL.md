---
name: call-analyzer
description: "Use when analyzing AI phone calls, measuring voice turn latency (EOU, STT, LLM TTFT, Chunker, TTS TTFB, first_audio), inspecting dialog transcripts, dialog state transitions, tool execution warnings, or debugging voice agent issues in Docker (PostgreSQL and robot-call-app logs)."
---

# Call Analyzer Skill

Diagnoses and analyzes voice calls made by the AI Voice Agent (`robot-call-v2`) running in Docker. Provides a turn-by-turn breakdown of latency in milliseconds, transcription accuracy, state transitions, and root-cause analysis for any delays or errors.

## Workflow

### 1. Identify the Target Call
Run a query against PostgreSQL to get the latest call attempts:
```powershell
docker exec robot-call-postgres psql -U robot_call -d robot_call -c "SELECT id, asterisk_channel, started_at, duration_sec, disposition, hangup_cause, recording_file_id FROM call_attempt ORDER BY id DESC LIMIT 5;"
```
Note the `id` and `asterisk_channel` (e.g. `1793874177.24`).

### 2. Fetch Call Transcripts and Dialog Flow
```powershell
docker exec robot-call-postgres psql -U robot_call -d robot_call -c "SELECT seq, role, text, dialog_state, ts_offset_ms FROM call_transcript WHERE call_id = <CALL_ID> ORDER BY seq;"
```

### 3. Check Overall Technical Metrics
```powershell
docker exec robot-call-postgres psql -U robot_call -d robot_call -c "SELECT call_id, stt_provider, tts_provider, llm_model, turn_count, avg_turn_latency_ms, max_turn_latency_ms, rtp_mos, prompt_tokens, completion_tokens FROM call_technical WHERE call_id = <CALL_ID>;"
```

### 4. Extract Turn-by-Turn Latency Breakdown from Logs
Using the channel ID found in step 1, search the application container logs:
```powershell
docker logs robot-call-app | Select-String "<CHANNEL_ID>" | Select-String "turn latency:", "speculation:", "turn closed after", "Smart Turn:", "LLM turn:"
```

#### Latency Stage Definitions:
* **`eou` (End of Utterance)**: The silence wait time before the VAD/SmartTurn model decides the caller has finished speaking (e.g. 350ms - 416ms).
* **`stt_final`**: Time from EOU until the recognizer's final transcript arrives.
* **`llm_ttft`**: Time to first token from the LLM after sending the prompt. (`0ms` indicates a Speculation Hit where the response was written ahead of time).
* **`chunk_wait`**: Time waiting for the LLM stream to complete the first speakable syntactic sentence.
* **`tts_ttfb`**: Time from sending the first sentence to TTS until the first playable PCM audio frame arrives.
* **`first_audio`**: Total elapsed silence experienced by the callee: from when they stopped speaking until AI audio starts playing.
$$\text{first\_audio} = \text{eou} + \text{stt\_final} + \text{llm\_ttft} + \text{chunk\_wait} + \text{tts\_ttfb}$$

### 5. Check for Warnings or Tool Faults
Inspect whether any tool calls failed or triggered double roundtrips:
```powershell
docker logs robot-call-app | Select-String "<CHANNEL_ID>" | Select-String "WARN", "ERROR", "model called unavailable tool", "empty LLM reply", "dropped"
```

### 6. Format the Report
Present the analysis with:
1. **Summary Table**: Call ID, duration, disposition, average & max latency, audio MOS score.
2. **Turn-by-Turn Latency Table**: Speaker text, EOU, STT, LLM TTFT, Chunker, TTS TTFB, total `first_audio`.
3. **Bottleneck & Improvement Analysis**: Highlight any speculation hits, double roundtrips, or model hesitations.
