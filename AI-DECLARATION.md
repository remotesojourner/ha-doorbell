---
version: "0.1.2"
level: auto
processes:
  design: assist
  implementation: auto
  testing: auto
  documentation: auto
  review: none
components:
  app/src/: auto
  blueprints/: assist
---

This format is based on [AI-DECLARATION.md](https://ai-declaration.md/en/0.1.2).

## Notes

- **Design & Architecture**: The core architecture, including the decision to use Frigate's `go2rtc` for WebRTC streams and Reolink's native snapshot API for fast notifications, was driven entirely by the human developer. The AI assisted strictly in technical integration discussions.
- **Android Implementation**: The AI coding assistant generated the Kotlin Jetpack Compose UI, DataStore preferences logic, OkHttp WebSocket integrations, and AndroidX Biometric authentication components autonomously based on human prompts.
- **Testing**: UI and unit tests using Robolectric and Compose Test Rules were written by the AI and verified by the human developer, who actively reviewed the code and caught edge cases (e.g., test state pollution).
- **Home Assistant Automations**: The human developer wrote the base YAML logic for the Reolink fast-snapshot automation. The AI assisted by refactoring that human-written YAML into a fully parameterized Home Assistant Blueprint.
- **Documentation**: The AI formatted and wrote README updates, markdown artifacts, and this declaration file under the explicit guidance and review of the human developer.
