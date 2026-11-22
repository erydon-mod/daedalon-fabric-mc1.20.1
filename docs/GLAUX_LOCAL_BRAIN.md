# Glaux's local knowledge and question recognition

Glaux recognises a submitted English request with a small learned semantic and
word/character classifier, then applies reviewed answer and context rules. It
does not generate new facts, train on player text, execute commands, inspect
private chat, scan a build, upload files or call an online model.

The Java 17 implementation lives in `client/glaux/dialogue`. It loads its
immutable model once on the single `Daedalon-Glaux` daemon worker. The same
worker processes later submissions; rendering and ordinary client ticks do
not run language inference. UI callbacks must be marshalled onto the Minecraft
client thread. Public session history getters return synchronized snapshots.

The knowledge covers basic use of ERYDON, Daedalon, Themelios, Metrakron and
Lithotheca, general style/composition principles and bounded troubleshooting.
The client supplies held-item identity and loaded-mod/pack metadata only when
needed. Loaded client versions are not evidence of server support, publication
or release compatibility. Exact unsupported IDs and catalogue combinations
continue to the website rather than being invented.

## Model provenance and resource budget

The packaged model preserves the full 64-dimensional Potion hybrid used by
the reviewed local prototype. The semantic input is
[`minishlab/potion-base-2M`](https://huggingface.co/minishlab/potion-base-2M),
pinned to `389b9f64be5aa4ae7a6bc6fe95ef20ce485ae5da`, with per-token int8
quantisation, Glaux intent prototypes/classification head and a separately
trained hashed word/character classifier. The model is MIT licensed; the
notice and full licence are packaged beside the model. No new inference
runtime or provider credentials are required.

The gzip file is the existing packed model's decoded gzip payload, not a new
model or reduction in its learned weights. Runtime answer resources omit
development-only absolute workstation paths and dry-run UI wording. On the
initial implementation build the files total 2,509,560 bytes on disk and
3,719,727 bytes with the model JSON decompressed. Including the independently
compiled dialogue classes measured approximately 3.86 MB, within the explicit
5,000,000-byte brain budget. This does not describe JVM heap usage, complete
mod size or owl geometry. The budget unit test checks decompressed resources.

The six original JavaScript parity probes retain their intent rankings; Java
confidence values agree within 2e-7. These are inference-port checks, not proof
that arbitrary wording is understood. The earlier independent prototype test
found 12 useful replies across 33 turns. New guards and clarification support
must not be presented as a new independent accuracy score.

The first independent Java evaluation froze 23 conversations / 30 turns before
calling this implementation. Manual review found 12 useful replies, 3 partly
useful replies and 15 failures, including 4 confidently off-topic or
contradictory replies. It found no harmful instructions, incorrect control
instructions or invented registry IDs in that sample. The initial capture and
review remain unchanged in `build/reports/glaux-java-independent-eval.json`;
this small adversarial sample is not a population accuracy estimate.

After that evaluation, routing guards require positive topic evidence before a
learned label may select an authored answer. They check simple clause-local
negation, give explicit questions priority over incidental held-item words,
keep correction and diagnostic context, and offer website continuation when a
clarification remains unresolved. All 53 local Java regression tests pass,
including 14 additional behaviour tests informed by the observed failure
classes. Those are development regressions, not a new holdout result. No model
or knowledge-library changes were made in this pass. Compound requests,
unfamiliar paraphrases and complex negation can still exceed this bounded
recogniser; the safe fallback is clarification or an exact-question handoff.

A second independently frozen follow-up used 11 conversations / 13 fresh
turns and found 5 useful, 3 partly useful and 5 failed replies, including 3
assertive wrong-task or contradictory replies. Its separate report is
`build/reports/glaux-java-followup-eval.json`. The prompts differ from the first
set, so their percentages must not be compared as an accuracy trend. Later
development regressions cover operation-specific property selection,
object-only versus operation-changing corrections, retained tool context,
catalogue/menu first-opening observations and style exclusion verbs. Both
independent captures remain unchanged; passing those regression checks does
not establish a new independent usefulness score.

A small, explicit display-only wording pass removes editorial prefixes such
as “reviewed” from ordinary control guidance and “inspected default opener”
from the picker introduction. It never rewrites player questions or changes
the authored knowledge resources. Meaningful version-scoped renderer,
dependency and unverified server-compatibility cautions remain visible.

## Conversation and website continuation

An uncertain request first asks a meaningful clarification with up to three
choices. Submitted numbers, ordinals and clear free-text replies map directly
to the stored meanings. Ambiguous “yes” and invalid numbers preserve the
original question and choices. Object corrections replace the subject without
losing the requested operation; third-party objects never inherit Daedalon
controls solely because Daedalon is loaded. Directed banter may request one
finite shake, subject to session stillness, teasing preference and a cooldown.
Quotation and substrings do not count as directed insults.

`HandoffCodec` creates only an allowlisted HTTPS URL to
`https://www.erydon.co.uk/ask`. It does not open a browser or submit an enquiry.
The player can edit the separate pending question before creating the link.
The payload contains v1, a random UUID, source `daedalon`, the exact enquiry,
up to eight prior lower-trust turns, an optional five-mod version scope and
`historyTruncated`. The current pending enquiry is omitted from prior user
turns, so the website receives one new question rather than a duplicate.

Limits are 2,000 UTF-16 question units, 1,000 per prior turn, 3,000 total prior
units, 6,144 decoded UTF-8 JSON bytes and 8,192 base64url fragment characters.
Old context is dropped as whole turns to fit. If even the exact question cannot
fit, `Export.uri()` is null and the full `fallbackText()` remains available;
the enquiry is never silently shortened. Context roles are `user`,
`local_authored` and `local_generated`, never privileged assistant/system roles.
The decoder rejects unknown fields, duplicate keys, malformed UTF-8, unknown
roles/versions, invalid scope and a duplicated final prior user enquiry.

The actual Java sender's representative JSON, encoded fragment and URL fixture
are in `src/test/resources/glaux/handoff`. Website receiver tests can consume
them without reproducing or guessing the serializer.
