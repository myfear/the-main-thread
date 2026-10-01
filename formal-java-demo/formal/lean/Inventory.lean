import Lean.Elab.Tactic.Omega

def reserve (available : Int) : Int :=
  if available > 0 then available - 1 else available

theorem reserve_preserves_nonnegative
    (available : Int) (h : 0 <= available) :
    0 <= reserve available := by
  unfold reserve
  split
  · omega
  · omega
