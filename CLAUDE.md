<!-- planager:start -->

# Feature Plans

This project uses **planager** for structured feature planning. Plans are
HTML files in `.plans/` with phased steps and checkboxes.

## Automatic behavior

### On session start

Check `.plans/` for any plans with `status: in-progress` or `status: blocked`.
If any exist, briefly note them to the user (e.g. "There's an in-progress plan
for <title>"). If the user's request clearly relates to one, read it and resume
from the first unchecked step. Don't force it - if the user is asking about
something unrelated, just mention the plan exists and move on.

### When starting new feature work

Before writing code for a non-trivial feature, create a plan:

1. Ask the user for a brief description (if not already provided).
2. Explore the codebase to understand what's involved.
3. Draft a phased plan with concrete, checkable steps.
4. Present the plan to the user for approval.
5. Save the approved plan to `.plans/<feature-slug>.html`.
6. Begin implementation from Phase 1.

Skip planning for trivial tasks (single-file fixes, typos, config changes).
Use judgment - if the work spans multiple files or sessions, it deserves a plan.

### While working on a planned feature

- Check off steps (set `data-status="done"` and add class `done`) as they are completed.
- Add notes to the `## Notes` section for decisions, blockers, or alternatives
  considered.
- Update the `updated` date in frontmatter.
- Set `status: in-progress` when work begins (if still `planning`).
- If blocked, set `status: blocked` and note the reason.

### On completion

- Set `status: done` in frontmatter.
- Write a brief summary in `## Notes` of what was built.
- Check off all remaining steps.

## Plan format

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Human-Readable Title</title>
<meta name="feature" content="short-slug">
<meta name="status" content="planning">
<meta name="created" content="YYYY-MM-DD">
<meta name="updated" content="YYYY-MM-DD">
<style>
  body { font-family: system-ui, sans-serif; max-width: 48rem; margin: 2rem auto; padding: 0 1rem;
         line-height: 1.6; }
  .phase { margin: 1.5rem 0; }
  .step { padding: 0.25rem 0; }
  .step::before { content: "\2610"; margin-right: 0.5rem; }
  .step.done::before { content: "\2611"; }
</style>
</head>
<body>

<h1>Human-Readable Title</h1>

<section id="context">
<h2>Context</h2>
<p>What the feature is, why it matters, constraints, links to issues or docs.</p>
</section>

<section class="phase" id="phase-1">
<h2>Phase 1: &lt;title&gt;</h2>
<p>Brief description of this phase.</p>
<div class="step" data-status="pending">Step description</div>
<div class="step" data-status="pending">Step description</div>
</section>

<section class="phase" id="phase-2">
<h2>Phase 2: &lt;title&gt;</h2>
<div class="step" data-status="pending">Step description</div>
</section>

<section id="notes">
<h2>Notes</h2>
<p>Running log of decisions, blockers, things tried.</p>
</section>

</body>
</html>
```

<!-- planager:end -->
