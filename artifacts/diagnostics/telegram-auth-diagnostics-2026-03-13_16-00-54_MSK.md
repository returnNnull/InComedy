# Telegram Server Diagnostics Export

- Fetched at: `2026-03-13T13:00:54Z`
- Time window start: `2026-03-12T21:00:00Z` (local day start in `Europe/Moscow`)
- Base URL: `https://incomedy.ru`
- Raw JSON: `/Users/abetirov/AndroidStudioProjects/InComedy/artifacts/diagnostics/telegram-auth-diagnostics-2026-03-13_16-00-54_MSK.json`

## /api/v1/auth/telegram

- Event count: `14`
- Stage counts:
  - `auth.telegram.start.success`: `14`
- Status counts:
  - `200`: `14`
- Request IDs: `f8ead6f3-5cd1-4b83-bd98-3f5bdc5aef1d, 12318b19-4d77-4dc9-acdd-718336489e11, af879a84-b673-4819-86b3-507f1da0b746, 1efa2d1f-93df-4526-b529-5e220d4d5870, b555eb7c-6b7b-4366-babe-cfc28157e3d1, 6ce51649-8d66-41f3-9c59-1a5fd204a94e, 06e22a81-9b9d-4dc7-8937-96cac1c0f113, a5863f20-5458-4038-9511-7dcb8c2b6e42, 9e155435-754b-4c2c-82b4-07f6e7bd365f, c0548e4a-f107-4f89-a66e-c92f84b7fe08, 267402b2-5bd5-4fcf-a89c-5498513feee9, ded20848-1077-4fff-a596-0e9ec0ec5fa9`
- Most recent events:
  - `2026-03-13T12:58:39.809870942Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `f8ead6f3-5cd1-4b83-bd98-3f5bdc5aef1d`
  - `2026-03-13T12:57:12.076754284Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `12318b19-4d77-4dc9-acdd-718336489e11`
  - `2026-03-13T12:56:08.321922476Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `af879a84-b673-4819-86b3-507f1da0b746`
  - `2026-03-13T12:55:52.876618052Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `1efa2d1f-93df-4526-b529-5e220d4d5870`
  - `2026-03-13T12:48:06.028710424Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `b555eb7c-6b7b-4366-babe-cfc28157e3d1`
  - `2026-03-13T12:47:53.808689238Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `6ce51649-8d66-41f3-9c59-1a5fd204a94e`
  - `2026-03-13T12:46:15.515089425Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `06e22a81-9b9d-4dc7-8937-96cac1c0f113`
  - `2026-03-13T12:45:04.981214240Z` | `/api/v1/auth/telegram/start` | `auth.telegram.start.success` | status `200` | request `a5863f20-5458-4038-9511-7dcb8c2b6e42`

## /auth/telegram/launch

- Event count: `31`
- Stage counts:
  - `auth.telegram.launch.bridge.client_event`: `20`
  - `auth.telegram.launch.bridge.ready`: `11`
- Status counts:
  - `200`: `11`
  - `204`: `20`
- Request IDs: `ebe230ed-2c01-49b3-8ffa-cb1733f8ac4a, 9cbff0a0-1f5d-400e-b6d4-1dd35aff7170, b2b2c531-8ac6-4af6-b433-bc6d7fa43e84, 5e1f3b86-ad03-4fe6-995f-ea8908618862, de7360d3-d5e1-4c52-8b84-6acfc6c3c6b7, 110e2252-ff20-4448-ab99-61d1c8da1886, ffc17303-1cc7-43d2-9d4a-d461aa182efb, 4929434e-45e9-4568-8a03-fe16bf4e3261, faf17df4-b594-4136-86b1-f35fdea79db4, 98b23d05-dd4d-45e0-81af-84445399c59e, 4ef6e705-cb92-42d7-bcba-ee95ff581d55, 986bb8d9-6f34-48ea-9b9b-7645f79044d8`
- Most recent events:
  - `2026-03-13T12:58:41.695618097Z` | `/auth/telegram/launch/telemetry` | `auth.telegram.launch.bridge.client_event` | status `204` | request `ebe230ed-2c01-49b3-8ffa-cb1733f8ac4a`
  - `2026-03-13T12:58:41.572145155Z` | `/auth/telegram/launch/telemetry` | `auth.telegram.launch.bridge.client_event` | status `204` | request `9cbff0a0-1f5d-400e-b6d4-1dd35aff7170`
  - `2026-03-13T12:58:41.170075504Z` | `/auth/telegram/launch/telemetry` | `auth.telegram.launch.bridge.client_event` | status `204` | request `b2b2c531-8ac6-4af6-b433-bc6d7fa43e84`
  - `2026-03-13T12:58:41.050697725Z` | `/auth/telegram/launch/telemetry` | `auth.telegram.launch.bridge.client_event` | status `204` | request `5e1f3b86-ad03-4fe6-995f-ea8908618862`
  - `2026-03-13T12:58:40.647516Z` | `/auth/telegram/launch` | `auth.telegram.launch.bridge.ready` | status `200` | request `de7360d3-d5e1-4c52-8b84-6acfc6c3c6b7`
  - `2026-03-13T12:57:14.871296666Z` | `/auth/telegram/launch/telemetry` | `auth.telegram.launch.bridge.client_event` | status `204` | request `110e2252-ff20-4448-ab99-61d1c8da1886`
  - `2026-03-13T12:57:14.743388898Z` | `/auth/telegram/launch/telemetry` | `auth.telegram.launch.bridge.client_event` | status `204` | request `ffc17303-1cc7-43d2-9d4a-d461aa182efb`
  - `2026-03-13T12:57:14.211021556Z` | `/auth/telegram/launch/telemetry` | `auth.telegram.launch.bridge.client_event` | status `204` | request `4929434e-45e9-4568-8a03-fe16bf4e3261`

## /auth/telegram/callback

- Event count: `0`
- No events in this window.

