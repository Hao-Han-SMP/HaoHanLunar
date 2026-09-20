# Audit Uncertain Items & Clarifications

This document records items where intent or usage is uncertain or dual-layered, ensuring code is preserved rather than prematurely deleted.

---

## 1. Dual Targeter Registries: `skill.target` vs `skill.targeter`
- **Location**:
  - `vn.haohan.lunar.api.system.combat.skill.target.TargeterRegistry`
  - `vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry`
- **Context**:
  - `skill.target` contains the MVP functional targeters (`self`, `trigger`, `target`, `threat`, `players_in_radius`).
  - `skill.targeter` contains Mythic-syntax regex parsers and geometric targeters (`@Cone`, `@Cylinder`, `@Line`, `@Sphere`, `@Ring`).
- **Status**: **PRESERVED**. Both registries are actively referenced in different subsystems and unit tests.

---

## 2. `ActiveLunarMob` Subclass Alias
- **Location**:
  - `vn.haohan.lunar.core.mob.ActiveLunarMob`
- **Context**:
  - Extends `vn.haohan.lunar.core.subsystem.mob.ActiveMob`.
  - Numerous test suites (`PublicEventApiTest`, `StatSystemTest`, `DamageContributionTest`, etc.) specifically type or instantiate `ActiveLunarMob`.
- **Status**: **PRESERVED** to avoid breaking test harnesses and backwards compatibility.

---

## 3. SkyboxEngine Dependency
- **Location**:
  - `vn.haohan.lunar.core.features.VisualMechanic`
- **Context**:
  - Soft dependency configured in `paper-plugin.yml` with `required: false`.
  - When absent on the server, `VisualMechanic` degrades gracefully without throwing `ClassNotFoundException`.
- **Status**: **PRESERVED**.
