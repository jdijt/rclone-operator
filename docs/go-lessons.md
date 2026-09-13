# Go lessons: recurring mistakes

Claude adds an entry when it flags the same kind of issue a second time (see CLAUDE.md,
"Mistakes log"). Newest first. Mark an entry **absorbed** once it stops coming up.

Format: what the reflex is → what idiomatic Go does instead → the most recent authoritative
Go doc explaining the pattern (go.dev blog/docs, Effective Go, Code Review Comments, the FAQ)
→ where it came up.

---

## Reaching for generics / type-level solutions  (seen: 2×, pre-log)

- **Reflex:** from Java/Scala, model variation with type parameters (e.g. a generic
  reconciler parameterised over the object type).
- **Go instead:** an interface, or a struct with function/interface fields set in a
  constructor. Use type parameters for containers and algorithms over many types
  (`slices`, `maps`, a typed cache), not to abstract behaviour. Same for
  inheritance-shaped hierarchies and extracting an interface before there's a second
  implementation: accept interfaces where you consume them, return concrete types.
- **Read:** [When To Use Generics](https://go.dev/blog/when-generics) (Ian Lance Taylor,
  go.dev blog) for when type parameters fit and when an interface is simpler;
  [Code Review Comments: Interfaces](https://go.dev/wiki/CodeReviewComments#interfaces) for
  defining interfaces at the consumer and returning concrete types.
- **Came up:** reconciler design, 2026-09-05.
- **Status:** open
