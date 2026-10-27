# UI wireframes and interaction boundaries — Phase 1

Design references Master Prompt III–IV, IX–XII and XV. **ASCII structural wireframes, not screenshots or device acceptance.**

## A. FULL CUSTOM DASHBOARD — independent Android head unit only

```text
  16:9 landscape / target 70% + 30% (relative; NOT fixed px)
  ┌────────────────────────────────────────┬─────────────────┐
  │  MAP 70%                               │ CALENDAR        │
  │  ┌──────────────────────────────────┐  │ 25% of right    │
  │  │ live GPS + map + road and signs  │  │ Solar/Lunar    │
  │  │                                  │  │ Events (short) │
  │  │  [GPS km/h] [LIMIT — or 60 PIP]  │  ├─────────────────┤
  │  │  [Free Drive / Follow vehicle]   │  │ PAYMENT IMAGE   │
  │  │                                  │  │ / QR            │
  │  │  navigation surface (future)     │  │ 55% of right    │
  │  │                                  │  │ no transaction │
  │  └──────────────────────────────────┘  │ state inferred │
  │                                        ├─────────────────┤
  │ safety alert never covered by widgets │ MEDIA           │
  │                                        │ 20% of right    │
  └────────────────────────────────────────┴─────────────────┘
```

Right column height ratios must be 25% calendar, 55% QR and 20% media by default (not 25/55/20 of *screen width*). Map 70% and side 30% of width. For 16:10 / 21:9 / ultrawide apply proportional layout, fixed physical minimum tap sizes and preserve map/PIP priority. At small viewport use smaller text, potentially scrolling or compact widgets rather than hiding safety information.

**Driving:** glanceable data; no drag/drop editor, text entry, QR manipulation, or camera controls. **Parked:** fullscreen QR/image selection, widget edits, map archive import.

## B. PHONE COMPANION — settings and layout editor

```text
 ┌──────────────────────────────────┐
 │ Thanh Taxi — Setup (parked)      │
 ├──────────────────────────────────┤
 │ Vehicle & Head Unit profile      │
 │ [16:9 ▼] [Preview / Connect]     │
 │                                  │
 │ Dashboard Layout Editor [FUTURE] │
 │ ┌─────────────────┬──────────┐   │
 │ │ Map [70%]       │ Cal      │   │
 │ │                 ├──────────┤   │
 │ │                 │ QR       │   │
 │ │                 ├──────────┤   │
 │ │                 │ Media    │   │
 │ └─────────────────┴──────────┘   │
 │ [Preset ▼] [Lock] [Reset]        │
 │                                  │
 │ Map offline packs / Data QA      │
 │ GPS & warnings / Calendar/QR     │
 │ Media / Android Auto status      │
 └──────────────────────────────────┘
```

Dragging/resizing in this panel is a **Phase 3 requirement** and not implemented just because the preview diagram exists.

## C. ANDROID AUTO — platform-compliant concept ONLY

```text
 ┌─────────────────────────────────────────────────┐
 │ Host-managed MapWithContentTemplate / map      │
 │  (map and route when legitimate navigation)   │
 │  supported host action strip / nav info       │
 │  no custom 70/30 calendar + QR + media grid   │
 └─────────────────────────────────────────────────┘
```

Car App Library templates are host-controlled. No promise of arbitrary panel positioning, full 4-widget sharing, drag/drop or payment QR while driving. App won't appear on Android Auto until a category-eligible `CarAppService` is implemented and tested in Phase 5.

## D. Screen-state matrix

| Screen state | Map | GPS km/h | speed limit/PIP | Calendar | QR | Media |
|---|---|---|---|---|---|---|
| fresh, verified on compatible head unit | render | trusted fix | evidence only | summary | image | permitted session |
| GPS invalid | last map/safe center | unknown | `—` / unknown | unchanged | unchanged | unchanged |
| map network disconnected | known-complete offline tile only; otherwise error | may still work | data only if valid | offline lunar | local image | local session |
| unverified sign/segment | map can render | valid GPS | `—` / not verified | unchanged | unchanged | unchanged |
| driving vs parked | navigation controls | glanceable | prominent | concise | do not ask driver to interact | simple controls |
| Android Auto active | eligible template map, not custom Activity | template-limited | host-compatible only | no arbitrary overlay | no arbitrary overlay | separate supported media integration |

## E. HMI design constraints

- Themes requested: Thanh Taxi Dark, Thanh Taxi Light, Cyberpunk Night, time-aware light/dark (Asia/Ho_Chi_Minh). **Phase 3.**
- High sunlight/night contrast, Vietnamese font and red only for high-salience critical warnings.
- Driver-distraction/host requirements override user preferences.
- QA device matrix to be recorded: 16:9, 16:10, 21:9, lower DPI, head-unit with restricted Android, Android Auto DHU and real target vehicle. **No claims of physical measurements yet.**
