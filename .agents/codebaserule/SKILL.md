---
name: codebaserule
description: Minecraft Plugin Engineering & Code Quality Skill. Enforces search-before-writing, reuse over duplication, minimal surgical changes, native Minecraft/Paper/Purpur/Folia API usage, scheduler and main-thread safety, plugin lifecycle discipline, prompt compliance, and requirement retention.
---

# Minecraft Plugin Engineering & Code Quality Skill

## Purpose

Maintain a clean, minimal, reusable, performant, and platform-native Minecraft plugin codebase.

When implementing or modifying anything, do not treat the requested feature as an isolated task.

Always consider:

* Existing project code
* Existing abstractions
* Existing utilities
* Existing APIs
* Minecraft/Paper/Purpur/Folia APIs
* Installed dependencies
* Existing architecture
* Existing conventions
* Performance implications
* Threading constraints
* Plugin lifecycle

The goal is **not to write more code**.

The goal is to make the **smallest correct change that naturally integrates with the existing project and Minecraft ecosystem**.

---

# 1. CORE ENGINEERING PRINCIPLE

Always follow this priority:

```text
Search existing code
        ↓
Reuse existing implementation
        ↓
Extend existing implementation if appropriate
        ↓
Use native Minecraft/server API
        ↓
Use Java standard library
        ↓
Use an already-installed dependency
        ↓
Write a small local implementation
        ↓
Add a new abstraction
        ↓
Add a new dependency
```

Do not skip directly to the bottom.

Before writing new code, ask:

1. Does this already exist?
2. Can I reuse it?
3. Can I extend it?
4. Can Minecraft/Paper/Purpur already do this?
5. Can Java's standard library do this?
6. Does an installed dependency already provide it?
7. Am I creating unnecessary architecture?
8. What is the smallest implementation that works?

---

# 2. SEARCH BEFORE WRITING

Before creating or modifying a:

* class
* method
* function
* utility
* helper
* component
* manager
* service
* provider
* factory
* wrapper
* interface
* abstraction
* registry
* constant
* configuration
* event handler
* validator
* converter
* data structure
* scheduler
* listener

search the existing codebase first.

Look for:

* Similar names
* Similar responsibilities
* Similar implementations
* Callers
* Implementations
* Interfaces
* Subclasses
* Utilities
* Event handlers
* Registrations
* Configuration
* Constants
* Related modules
* Related API calls

Do not create a parallel implementation without understanding the existing implementation.

---

# 3. REUSE BEFORE REWRITE

If existing code already performs the required operation, reuse it.

If the existing implementation is almost suitable, consider extending it instead of creating another implementation.

Prefer:

```text
reuse existing method
```

over:

```text
create another method with the same behavior
```

Prefer:

```text
extend existing abstraction
```

over:

```text
create a parallel abstraction
```

Prefer:

```text
use existing utility
```

over:

```text
create another utility
```

The new feature should feel like a natural extension of the existing project.

---

# 4. MINECRAFT API FIRST

Before implementing Minecraft-related functionality manually, check whether the server platform already provides an API for it.

Depending on the platform and version, check APIs such as:

* Bukkit API
* Spigot API
* Paper API
* Purpur API
* Folia-compatible APIs
* Adventure API
* PersistentDataContainer
* NamespacedKey
* ItemStack / ItemMeta
* Components
* Events
* Scheduler
* Commands
* Permissions
* Inventory API
* Entity API
* World API
* Block API
* Ray tracing
* Particles
* Sounds
* Configuration APIs
* Registries
* Server APIs
* Resource-pack related APIs

If the native API already solves the problem correctly, do not recreate it.

Example:

```java
// Prefer the platform API when it already provides the required behavior.
```

Do not replace a native API with a custom implementation without a concrete reason.

---

# 5. MINECRAFT ECOSYSTEM REUSE

When implementing a common Minecraft plugin feature, investigate whether an existing ecosystem API or installed dependency already provides the required functionality.

Check:

```text
Project code
        ↓
Minecraft/Paper/Purpur API
        ↓
Existing project abstraction
        ↓
Installed dependency
        ↓
Java standard library
        ↓
Small custom implementation
```

Do not add a new dependency simply because it provides a convenient helper when the project already has a suitable solution.

---

# 6. DO NOT DUPLICATE FUNCTIONALITY

Avoid multiple implementations of the same logical responsibility.

Pay particular attention to duplicate:

* Validation
* Parsing
* Conversion
* Formatting
* Rendering
* State updates
* Event handling
* Serialization
* Deserialization
* Networking
* Resource loading
* Calculations
* Collection manipulation
* Lifecycle management
* Error handling
* Minecraft API calls
* Player lookups
* Item creation
* Item validation
* Permission checks
* Configuration access
* Cooldown handling

If two implementations perform the same responsibility, prefer one shared implementation when the semantics are genuinely the same.

Do not duplicate logic merely because the new feature is located in another class.

---

# 7. DO NOT OVER-ABSTRACT

Do not create architecture simply because code could theoretically be reused.

Avoid unnecessary:

* Managers
* Services
* Factories
* Providers
* Interfaces
* Wrappers
* Registries
* Generic frameworks
* Utility classes
* Extension systems
* Configuration layers

Especially avoid classes such as:

```text
SomethingManager
SomethingService
SomethingProvider
SomethingFactory
```

when they only contain one trivial method or merely forward calls.

An abstraction is justified when it provides meaningful:

* reuse
* separation of responsibility
* lifecycle management
* API boundary
* testability
* independent implementations
* complexity reduction

Otherwise, keep the code simple.

---

# 8. AVOID PASS-THROUGH WRAPPERS

Do not create:

```java
public void doSomething() {
    existingObject.doSomething();
}
```

unless the wrapper provides meaningful behavior or establishes a real architectural boundary.

Do not wrap Minecraft APIs simply to rename them.

Do not create wrappers solely because "everything should go through a manager".

---

# 9. AVOID PREMATURE GENERALIZATION

Do not design for hypothetical future requirements.

Do not create:

* Generic plugin frameworks
* Universal registries
* Generic event systems
* Generic configuration systems
* Generic service layers
* Speculative extension points
* Large abstractions for one current feature

Implement the actual requirement first.

Generalize only when repeated real usage demonstrates that the abstraction is needed.

---

# 10. MINIMAL CHANGE

Make the smallest change that correctly solves the problem.

Do not:

* Rewrite unrelated code
* Refactor entire files unnecessarily
* Rename unrelated symbols
* Reorganize unrelated packages
* Introduce new architecture without need
* Replace working implementations for stylistic preference
* Add defensive code without a concrete reason
* Change APIs unnecessarily
* Modify unrelated systems

A feature request does not automatically justify refactoring surrounding code.

---

# 11. PRESERVE PROJECT STYLE

Follow the existing project's:

* Naming conventions
* Package structure
* Architecture
* Formatting
* Class organization
* Method patterns
* Lifecycle patterns
* Error handling conventions
* Dependency choices
* Event conventions
* Configuration conventions
* Logging conventions

Do not impose a different coding style merely because another style is personally preferred.

Consistency with the existing codebase is more important than introducing a new style.

The final code should look like it belongs to the project.

---

# 12. PLUGIN LIFECYCLE

Respect the plugin lifecycle:

```text
onLoad
   ↓
onEnable
   ↓
runtime
   ↓
onDisable
```

Do not access resources before they are initialized.

Do not register things multiple times.

Do not create duplicate scheduler tasks.

Do not leave listeners, tasks, connections, or resources orphaned.

During shutdown, correctly handle resources that require cleanup:

* Tasks
* Executors
* Database connections
* Network connections
* Files
* Caches
* External resources

Use the project's existing lifecycle mechanisms when available.

---

# 13. EVENT-DRIVEN DESIGN

Prefer event-driven behavior over unnecessary polling.

Prefer:

```text
Event
 ↓
Validation
 ↓
Existing logic/service
```

over:

```text
Every tick
 ↓
Scan everything
 ↓
Check whether something happened
```

Do not create repeating tasks when an event can provide the required information.

If a repeating task is genuinely required, use the lowest reasonable frequency.

Do not run logic every tick unless the behavior actually requires tick-level precision.

---

# 14. PERFORMANCE

Minecraft servers are highly sensitive to main-thread workload.

Pay special attention to:

* Tick-time operations
* Entity iteration
* Player iteration
* World scanning
* Block scanning
* Chunk operations
* Inventory operations
* Serialization
* Database queries
* Network requests
* File I/O
* Object allocation
* Repeated API lookups
* Scheduler frequency

Avoid:

```text
O(all players)
O(all entities)
O(all blocks)
```

when a direct lookup or event-driven approach exists.

Do not optimize blindly.

First determine whether the operation is actually performance-sensitive.

---

# 15. MAIN THREAD VS ASYNC

Understand which operations are safe on which thread.

Minecraft server APIs are often main-thread constrained.

Do not perform unsafe Bukkit/Paper/Purpur world operations from asynchronous threads.

Be especially careful with:

* World modification
* Entity modification
* Player state
* Inventory
* Blocks
* Chunks
* Commands
* Many server APIs

Database, filesystem, and network operations may require asynchronous execution when appropriate.

Separate:

```text
Main-thread Minecraft operations
```

from:

```text
Async-safe operations
```

Do not blindly move an entire operation async.

Only move the portion that is actually safe to execute asynchronously.

---

# 16. SCHEDULER DISCIPLINE

Before creating a scheduled task, ask:

1. Does a task already perform this?
2. Can an event replace this task?
3. Does this really need to run every tick?
4. Can the interval be longer?
5. Is the task cancelled correctly?
6. Could multiple copies of the task be created?

Never create duplicate repeating tasks accidentally.

Track lifecycle when necessary.

---

# 17. DATA STRUCTURES

Choose data structures according to actual access patterns.

Prefer:

```text
Map<K,V> → lookup by key
Set<T>   → membership
List<T>  → ordered collection
Deque<T> → queue/stack behavior
```

Do not use a List for repeated lookup when a Map or Set is the correct structure.

Do not introduce complicated caching without evidence that caching is useful.

Caching requires a clear:

* ownership
* lifecycle
* invalidation strategy

---

# 18. CACHING

Do not cache everything.

Cache only when:

1. Data is accessed frequently.
2. Lookup or computation is actually expensive.
3. Cache invalidation is manageable.
4. The cache does not introduce more complexity than the problem itself.

Do not create a global cache simply because repeated access exists.

---

# 19. API VERSION COMPATIBILITY

Before using an API:

1. Identify the Minecraft version.
2. Identify the server platform.
3. Identify the platform version.
4. Confirm the API exists in that version.
5. Check whether the API is deprecated.
6. Check the project's existing compatibility approach.

Do not assume that an API available in a newer Paper version exists in the project's target version.

Do not use implementation-specific internals unless there is a concrete reason.

Prefer stable public APIs.

---

# 20. DEPENDENCY DISCIPLINE

Before adding a dependency:

1. Search the project.
2. Check existing dependencies.
3. Check the Minecraft/server API.
4. Check Java standard library.
5. Determine whether the dependency is genuinely necessary.

Do not introduce a dependency for a small function that can be implemented using existing APIs.

Do not introduce multiple libraries that solve the same problem.

---

# 21. OPTIONAL DEPENDENCIES

If a feature depends on an optional plugin/library:

* Detect availability correctly.
* Isolate the integration when practical.
* Do not expose the optional dependency throughout unrelated code.
* Follow the project's existing compatibility mechanism.
* Do not silently fail unless that behavior is intentional.
* Do not use broad exception handling to hide missing dependencies.

If an optional dependency is unavailable, follow the project's established fallback behavior.

---

# 22. EXCEPTION HANDLING

Avoid:

```java
catch (Throwable ignored) {}
```

and:

```java
catch (Exception ignored) {}
```

unless there is a concrete, documented reason.

Prefer the narrowest appropriate exception type.

Do not swallow errors that should be visible.

Do not use exceptions as normal control flow when a simpler mechanism exists.

Do not add try/catch blocks merely to make code "safe".

When existing code intentionally suppresses an exception for compatibility, preserve that behavior unless there is a clear reason to change it.

---

# 23. IMPORT DISCIPLINE

When modifying a file:

* Remove genuinely unused imports.
* Avoid unnecessary dependencies between packages.
* Do not import another utility when an existing local implementation is appropriate.
* Do not introduce optional dependencies unnecessarily.
* Do not remove unusual imports without verifying their usage.

Keep imports consistent with the existing project style.

---

# 24. CONSTANT DISCIPLINE

Before creating a constant, search whether the same concept already exists.

Do not create multiple constants representing the same semantic value.

However, do not merge unrelated constants merely because they currently contain the same number or string.

For example, two unrelated values both being:

```java
20
```

does not mean they should share a constant.

Semantic meaning matters more than equal values.

---

# 25. RESOURCE MANAGEMENT

Avoid repeated initialization and repeated resource loading.

Check for:

* Duplicate configuration loading
* Duplicate resource loading
* Duplicate registration
* Duplicate database initialization
* Duplicate network initialization
* Duplicate cache initialization
* Duplicate scheduler registration

If an existing lifecycle already manages a resource, reuse it.

---

# 26. CONFIGURATION

Only expose values to configuration when there is a practical reason for server administrators to change them.

Good candidates may include:

* Messages
* Cooldowns
* Limits
* Permissions
* Feature toggles
* World names
* Effects
* Sounds
* Gameplay parameters

Do not put every constant into configuration.

Avoid configuration systems for values that are fundamentally implementation details.

---

# 27. COMMANDS AND PERMISSIONS

Reuse the project's existing command and permission architecture.

Do not create a second command registration system.

Do not duplicate permission checks if the project already has a standard permission utility or mechanism.

Keep command handlers focused on:

```text
Input
 ↓
Validation
 ↓
Existing business logic
 ↓
Response
```

Do not place large business systems directly inside command executors.

---

# 28. ITEMS AND PERSISTENT DATA

When implementing custom Minecraft items, first check whether the project already has an item creation/validation system.

Prefer native mechanisms such as:

```text
ItemStack
ItemMeta
PersistentDataContainer
NamespacedKey
Adventure Components
```

when appropriate.

Do not create custom metadata systems when the native persistent data system satisfies the requirement.

Reuse existing item identifiers and namespaces.

Do not duplicate item validation logic across listeners and commands.

---

# 29. ENTITY AND WORLD LOGIC

When working with entities, blocks, worlds, or players:

* Prefer native APIs.
* Avoid scanning unnecessarily large areas.
* Avoid repeated lookups.
* Avoid per-tick world scans when events can be used.
* Respect thread restrictions.
* Reuse existing world/entity utilities.
* Avoid custom implementations of functionality already provided by the server API.

---

# 30. DATABASE AND I/O

Never perform expensive database, filesystem, or network operations on the main server thread unless there is a concrete reason and the operation is known to be safe and sufficiently cheap.

Reuse existing database/repository infrastructure.

Do not create a second database abstraction when one already exists.

Do not create duplicate connection pools or resource managers.

---

# 31. CODE OWNERSHIP

Put logic in the class that naturally owns the responsibility.

Do not move logic into a global utility simply because it is technically reusable.

For example:

```text
Item behavior → item-related abstraction
Player state → player-related abstraction
Configuration → configuration owner
Database access → existing persistence layer
Event reaction → listener + existing business logic
```

Reuse does not mean "put everything into Utils".

---

# 32. DUPLICATION REVIEW

When adding code, compare it against existing code for:

* Same method behavior
* Same validation
* Same conversion
* Same API calls
* Same state transitions
* Same event conditions
* Same constants
* Same configuration access
* Same error handling
* Same resource loading
* Same scheduler logic

If duplication is found, determine the correct abstraction level.

Possible solutions:

```text
Reuse existing method
        ↓
Extend existing class
        ↓
Move logic to its natural owner
        ↓
Extract a small private method
        ↓
Extract a shared utility
        ↓
Create a real abstraction only when justified
```

Do not automatically create a global utility.

---

# 33. DO NOT OVER-CLEAN

Not every repeated line is a problem.

Do not refactor merely because:

* Two lines look similar.
* Two methods currently have identical implementations.
* Two values happen to be equal.
* A helper could theoretically be reused.
* An abstraction is theoretically possible.

Consider:

* Semantic responsibility
* Coupling
* Readability
* Number of consumers
* Expected change patterns
* Actual reuse

Avoid abstraction for abstraction's sake.

---

# 34. CODEBASE-WIDE AUDITS

For large changes or explicit audits, do not inspect only the files mentioned in the request.

Inspect the relevant source tree and search for:

* Duplicate implementations
* Duplicate helpers
* Duplicate constants
* Duplicate event handlers
* Duplicate API calls
* Duplicate validation
* Duplicate state management
* Unnecessary wrappers
* Unnecessary abstractions
* Unnecessary dependencies
* Unused imports
* Dead code
* Unreachable code
* Broad exception handling
* Swallowed exceptions
* Repeated initialization
* Repeated resource loading
* Redundant allocations
* Duplicate scheduler tasks
* Unsafe async operations
* Deprecated APIs
* Platform-specific incompatibilities

The purpose is to understand how the system actually works before changing it.

---

# 35. REUSE MAP

For a significant feature, first identify the relevant existing infrastructure.

Create an internal reuse map:

```text
Feature
 ├── Existing classes
 ├── Existing methods
 ├── Existing utilities
 ├── Existing events
 ├── Existing managers/services
 ├── Existing Minecraft APIs
 ├── Existing dependencies
 └── Existing configuration
```

Use this map to determine what can be reused before writing new code.

Do not create new infrastructure if the reuse map already provides the required capability.

---

# 36. IMPLEMENTATION WORKFLOW

For a non-trivial task, follow this workflow:

```text
1. Understand the requirement
        ↓
2. Identify the affected subsystem
        ↓
3. Inspect the existing code
        ↓
4. Search for similar implementations
        ↓
5. Trace callers and dependencies
        ↓
6. Identify reusable methods/classes
        ↓
7. Check Minecraft/Paper/Purpur/Folia APIs
        ↓
8. Check Java standard library
        ↓
9. Check installed dependencies
        ↓
10. Design the smallest change
        ↓
11. Implement
        ↓
12. Compile/test
        ↓
13. Search again for duplication
        ↓
14. Check lifecycle/thread safety
        ↓
15. Check performance
        ↓
16. Remove unnecessary code
```

Do not start by creating new classes.

---

# 37. BEFORE WRITING CODE

Internally answer:

```text
Does this already exist?
Can I reuse it?
Can I extend it?
Is there already a utility for this?
Does Minecraft/Paper already provide this?
Does Java already provide this?
Does an installed dependency provide this?
Am I duplicating a responsibility?
Am I creating unnecessary abstraction?
Am I adding unnecessary configuration?
Am I adding an unnecessary dependency?
Does this need to run on the main thread?
Does this need a scheduler?
Can an event replace polling?
What is the smallest change that works?
```

If an existing solution exists, use or extend it.

---

# 38. AFTER WRITING CODE

Perform a final engineering audit.

### Reuse

* Did I reuse existing code?
* Did I duplicate an existing method?
* Did I duplicate an existing utility?
* Did I duplicate validation?
* Did I duplicate a constant?
* Did I duplicate a scheduler?
* Did I duplicate an event handler?

### Minecraft API

* Could a native API replace custom code?
* Am I using the correct server platform API?
* Am I using the correct Minecraft version?
* Am I relying on deprecated or internal APIs unnecessarily?

### Architecture

* Did I create an unnecessary class?
* Did I create an unnecessary manager?
* Did I create an unnecessary service?
* Did I create an unnecessary wrapper?
* Did I create an unnecessary interface?
* Did I create an abstraction for hypothetical future requirements?

### Performance

* Am I doing unnecessary iteration?
* Am I polling when an event exists?
* Am I running tasks too frequently?
* Am I performing expensive operations on the main thread?
* Did I introduce unnecessary allocations?
* Did I introduce unnecessary caching?

### Dependencies

* Did I add a dependency unnecessarily?
* Does an installed dependency already solve this?
* Does Java already solve this?

### Scope

* Did I modify unrelated code?
* Did I perform unrelated refactoring?
* Did I change existing behavior unnecessarily?

If the answer reveals unnecessary complexity, simplify the implementation.

---

# 39. QUALITY BAR

Code is considered complete only when it is:

* Correct
* Minimal
* Reusable
* Consistent with the project
* Compatible with the target Minecraft/server version
* Efficient enough for a Minecraft server environment
* Thread-safe where required
* Lifecycle-safe
* Free of unnecessary duplication
* Free of unnecessary abstractions
* Free of unnecessary dependencies
* Integrated with existing project infrastructure

---

# 40. FINAL PRINCIPLE

Always remember:

```text
SEARCH FIRST.
REUSE SECOND.
CHECK NATIVE APIs THIRD.
SIMPLIFY FOURTH.
IMPLEMENT LAST.
```

The preferred implementation is:

```text
Existing code
    >
Existing abstraction
    >
Minecraft/Paper/Purpur/Folia API
    >
Java standard library
    >
Installed dependency
    >
Small local implementation
    >
New abstraction
    >
New dependency
```

Do not write more code simply because you can.

Do not create architecture simply because it looks cleaner in isolation.

Do not duplicate functionality simply because the new feature has a different name.

Do not replace existing project conventions with personal preferences.

**Make the minimum change that correctly solves the problem while maximizing reuse of the existing project and Minecraft ecosystem.**

The final code should feel like it was always part of the project.

# 41. PROMPT COMPLIANCE & REQUIREMENT RETENTION

When receiving a complex request, do not immediately start implementing.

First, internally decompose the request into explicit requirements.

A request may contain:

* Main objective
* Functional requirements
* Technical requirements
* Constraints
* Existing-code requirements
* Compatibility requirements
* Performance requirements
* UI/UX requirements
* Error-handling requirements
* Negative requirements
* Things that must NOT be changed
* Validation requirements
* Follow-up modifications

Do not treat the request as a single instruction.

Break it into individual requirements and preserve all of them throughout the implementation.

---

# 42. REQUIREMENT CHECKLIST

For complex requests, create an internal checklist before implementation.

Example:

```text
Requirements
├── R1: Main functionality
├── R2: Reuse existing implementation
├── R3: Use native Minecraft API where possible
├── R4: Preserve existing architecture
├── R5: Avoid duplicate logic
├── R6: Maintain compatibility
├── R7: Handle edge cases
└── R8: Do not modify unrelated systems
```

Every requirement must be accounted for before considering the task complete.

Do not silently drop a requirement because another part of the task appears more important.

---

# 43. REQUIREMENT PRIORITY

When instructions conflict, resolve them in this order:

```text
Explicit user requirement
        ↓
Existing project constraints
        ↓
Platform/API compatibility
        ↓
Security/correctness
        ↓
Performance
        ↓
Maintainability
        ↓
Style preferences
        ↓
Optional improvements
```

Never replace an explicit requirement with a personal interpretation simply because another approach appears cleaner.

If two requirements genuinely conflict, identify the conflict instead of silently choosing one.

---

# 44. PRESERVE NEGATIVE REQUIREMENTS

Pay special attention to instructions containing:

* Do not
* Don't
* Never
* Avoid
* Keep
* Preserve
* Must not
* Without changing
* Only change
* Do not modify unrelated code

Negative constraints are requirements too.

For example:

```text
"Fix X without changing Y"
```

means both:

```text
Fix X
+
Preserve Y
```

Do not treat the second part as optional.

---

# 45. REQUIREMENT TRACEABILITY

For significant tasks, maintain an internal mapping:

```text
User requirement
        ↓
Affected code
        ↓
Implementation
        ↓
Validation
```

Every important requirement should have a corresponding implementation or explicit reason why no code change is necessary.

Do not finish a task simply because the main feature works.

---

# 46. DO NOT STOP AT THE FIRST WORKING SOLUTION

A solution compiling successfully does not automatically mean the task is complete.

After implementation, verify:

```text
Does it compile?
Does it work?
Does it satisfy every requirement?
Does it preserve existing behavior?
Does it reuse existing code?
Does it use the appropriate Minecraft API?
Did it introduce duplication?
Did it introduce unnecessary architecture?
Did it introduce performance problems?
Did it violate any negative requirement?
```

Correctness is not only "the code compiles".

---

# 47. INTERRUPTION AND MID-TASK CHANGES

If the user changes, corrects, or extends the request while implementation is in progress:

1. Stop following the obsolete direction.
2. Identify the newly changed requirements.
3. Preserve still-valid requirements from the previous request.
4. Re-evaluate the current implementation.
5. Modify only what is necessary.
6. Do not continue blindly from the previous plan.

Treat the latest explicit user instruction as an update to the task specification.

Do not assume that the previous implementation plan is still valid.

---

# 48. CORRECTION HANDLING

When the user says:

```text
"Not this."
"Change X."
"Keep Y."
"Actually, do Z instead."
```

do not defend or continue the previous implementation.

Re-evaluate the affected requirement.

Determine:

```text
What changed?
What remains valid?
What existing code must be preserved?
What implementation must be reverted or changed?
```

Then continue from the corrected specification.

---

# 49. CONTEXT PRESERVATION

Do not lose previously established constraints when processing a follow-up request.

For example:

```text
Request 1:
Implement feature X using the existing architecture.

Request 2:
Add cooldown support.
```

The second request does not remove:

```text
Use existing architecture.
```

The resulting implementation must satisfy both unless the user explicitly changes the first requirement.

---

# 50. COMPLEX REQUEST HANDLING

For large requests, mentally divide the task into phases:

```text
Phase 1 — Understand
Phase 2 — Inspect
Phase 3 — Search
Phase 4 — Plan
Phase 5 — Implement
Phase 6 — Verify
Phase 7 — Cleanup
```

Do not mix all phases together.

This reduces the risk of implementing only the most obvious part of the request.

---

# 51. WEB / DOCUMENTATION RESEARCH

When external research is required, do not rely on the first result.

For Minecraft/API questions, prefer:

```text
Official documentation
        ↓
Official API reference
        ↓
Official project repository
        ↓
Established technical documentation
        ↓
Community sources
```

Verify important API details against the target version.

Do not assume an API exists simply because it exists in a newer version.

---

# 52. RESEARCH COMPLETENESS

When researching a technical question, explicitly identify:

```text
What is known?
What is version-dependent?
What is confirmed by documentation?
What is inferred?
What remains uncertain?
```

Do not silently convert assumptions into facts.

If a result is uncertain, verify it before basing implementation on it.

---

# 53. MULTI-SOURCE RESEARCH

When a task requires substantial research, gather information from multiple relevant sources when practical.

Compare:

* API documentation
* Existing implementations
* Project source code
* Version history
* Official repositories
* Dependency documentation

Do not simply collect many sources.

Use them to verify the actual technical conclusion.

---

# 54. SEARCH RESULT CROSS-CHECK

For important technical decisions:

```text
Search
  ↓
Find relevant source
  ↓
Verify source relevance
  ↓
Check target version
  ↓
Cross-check implementation/API
  ↓
Apply conclusion
```

Do not implement based solely on a search-result snippet.

---

# 55. DEEP TECHNICAL ANALYSIS

Avoid generic explanations when the task requires technical analysis.

Prefer explaining:

```text
Why
What
Where
How
Trade-offs
Constraints
Failure modes
Performance implications
Compatibility implications
```

For example, instead of:

```text
"This approach is better for performance."
```

explain the actual reason:

```text
"This avoids scanning every loaded entity every tick and instead reacts only when the relevant event is fired."
```

Use concrete engineering reasoning.

---

# 56. CODE-SPECIFIC REASONING

When recommending or implementing code, reason from the actual project.

Do not provide generic architecture that ignores:

* Existing classes
* Existing methods
* Existing dependencies
* Existing lifecycle
* Existing conventions
* Target Minecraft version
* Target server platform

The project itself is the primary source of truth for project-specific decisions.

---

# 57. AVOID GENERIC CODE

Do not generate boilerplate simply because it is a common pattern.

Before adding a pattern, verify that the project actually needs it.

Avoid statements such as:

```text
"Create a service layer because this is best practice."
```

without examining the existing architecture.

Use the simplest architecture that fits the actual project.

---

# 58. IMPLEMENTATION DEPTH

When the user requests a technical implementation, do not stop at superficial scaffolding.

If the task requires a complete feature, consider the full flow:

```text
Input
 ↓
Validation
 ↓
Business logic
 ↓
State changes
 ↓
Persistence / API interaction
 ↓
Events / side effects
 ↓
Error handling
 ↓
Cleanup
```

Do not implement only the visible entry point while leaving the underlying behavior incomplete.

---

# 59. EDGE CASE ANALYSIS

Before declaring a feature complete, consider relevant edge cases.

For Minecraft plugins, this may include:

* Player disconnects
* Player reconnects
* Plugin reload/disable
* World unload
* Entity removal
* Chunk unload
* Invalid item
* Missing configuration
* Missing dependency
* Duplicate event
* Duplicate task
* Concurrent access
* Server restart
* Version differences
* Permission changes

Only handle edge cases that are relevant.

Do not add speculative complexity for impossible or irrelevant cases.

---

# 60. FINAL REQUIREMENT AUDIT

Before finishing any significant task, perform an internal final audit:

```text
[ ] Every explicit requirement was addressed
[ ] Every negative constraint was preserved
[ ] Existing code was searched
[ ] Existing functionality was reused where appropriate
[ ] Native Minecraft APIs were checked
[ ] Existing dependencies were checked
[ ] No unnecessary dependency was added
[ ] No unnecessary abstraction was added
[ ] No duplicate logic was introduced
[ ] No unrelated code was changed
[ ] Minecraft version compatibility was checked
[ ] Thread safety was checked
[ ] Lifecycle behavior was checked
[ ] Performance implications were checked
[ ] Relevant edge cases were considered
[ ] The implementation actually works
```

Do not consider the task complete until this audit passes.

---

# 61. RESPONSE ACCURACY

When reporting completed work, distinguish between:

```text
Implemented
Verified
Assumed
Not tested
Not applicable
```

Do not claim something was tested if it was not actually tested.

Do not claim an API was verified if it was only assumed.

Do not claim the entire project was audited if only a subset was inspected.

Be precise about what was actually checked.

---

# 62. AVOID UNNECESSARY NARRATION

Do not spend excessive output explaining obvious implementation details.

Prefer:

```text
What changed
Why it changed
What was reused
What was verified
Remaining limitations
```

The codebase should contain the implementation details.

The response should communicate the important engineering decisions.

---

# 63. DECISION LOGIC

When multiple implementation approaches are possible, compare them against:

```text
Existing code reuse
Native API usage
Correctness
Compatibility
Performance
Complexity
Maintenance cost
Dependency cost
```

Prefer the solution with the smallest justified complexity.

Do not select an approach merely because it is architecturally fashionable.

---

# 64. PRIMARY AGENT RULE

Before every significant implementation decision, ask:

> "Am I solving the actual requirement, or am I solving a more complicated problem that I invented?"

If the latter, simplify.

The agent should optimize for:

```text
Requirement completeness
        +
Existing-code reuse
        +
Native API usage
        +
Correctness
        +
Performance
        +
Minimal complexity
```

Not for:

```text
Maximum abstraction
Maximum code
Maximum architecture
Maximum explanation
```

---

# 65. FINAL EXECUTION LOOP

For every substantial task, use this loop:

```text
UNDERSTAND
    ↓
EXTRACT REQUIREMENTS
    ↓
PRESERVE CONSTRAINTS
    ↓
INSPECT CODEBASE
    ↓
SEARCH FOR REUSE
    ↓
CHECK NATIVE APIs
    ↓
CHECK DEPENDENCIES
    ↓
DESIGN MINIMAL SOLUTION
    ↓
IMPLEMENT
    ↓
TEST / VERIFY
    ↓
AUDIT REQUIREMENTS
    ↓
AUDIT DUPLICATION
    ↓
AUDIT PERFORMANCE
    ↓
SIMPLIFY
    ↓
REPORT ACCURATELY
```

Never skip the final audit simply because the implementation compiles.

---

# 66. MASTER PRINCIPLE

The agent must optimize for **complete requirement fulfillment with minimum unnecessary change**.

Always remember:

```text
Understand everything.
Forget nothing.
Search before creating.
Reuse before rewriting.
Use native APIs before custom implementations.
Use existing dependencies before adding new ones.
Implement the smallest correct solution.
Verify every requirement.
Remove unnecessary complexity.
Report only what was actually verified.
```

The final implementation should be:

```text
Correct
+
Complete
+
Minimal
+
Reusable
+
Platform-native
+
Version-compatible
+
Performant
+
Maintainable
+
Consistent with the existing codebase
```

The code should feel like it was written as part of the project from the beginning, not generated independently and inserted afterward.
