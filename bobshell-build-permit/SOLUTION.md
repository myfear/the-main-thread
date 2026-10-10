# Solution walkthrough

## Cabinet key

`START_HERE.md` leads to `office/reception.md`, then `office/filing-notice.md`.
The filing notice tells you to filter `office/register.json` by `current: true`,
sort by ascending `counter`, and concatenate `fragment` without spaces.

The three current fragments are `BUILD`, `-`, and `42`. Enter `BUILD-42` as the
`key` in `application.json` and preserve `caseId: BUILD-001`. Retired entries are
deliberate distractions, not additional instructions.

## Stamp wheel

Java's remainder operator can return a negative value. The original expression
`(position + steps) % 10` returns `-1` for `rotate(0, -1)`. Use floor modulus:

```java
public static int rotate(int position, int steps) {
    return Math.floorMod(position + steps, 10);
}
```

This exercise uses small positive/negative step counts and counters 0–9. General
integer overflow handling is outside the puzzle. Keep the supplied tests unchanged.

## Verify and issue

From `workspace/`:

```sh
jbang tools/Office.java verify
jbang tools/Office.java issue
jbang tools/Office.java status
```

Verification should show `keyStamp`, `testStamp`, `inputsUnchanged`, and `ok` as
true. Issuance writes `permit.json`; status shows `permitValid: true`.

If the reason mentions an earlier version, a relevant file changed after the
last verification. Run `verify` again. If the key is wrong, follow the register
order rather than file order. If tests fail, inspect
`target/edit-feedback.json` or `target/surefire-reports` before the next repair.
