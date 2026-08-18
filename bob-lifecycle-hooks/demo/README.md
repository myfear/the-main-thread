# Bob Lifecycle Hooks Lab

This small Python project is the runnable fixture for the IBM Bob lifecycle-hooks tutorial. The unit-test baseline is green. The acceptance suite starts red because it describes the feature Bob should implement.

Open this directory as the Bob workspace, trust it after reviewing `.bob/settings.json` and `.bob/hooks/`, and run `/upgrade-plan`.

The task may edit `app/`, `tests/`, and this `README.md`. The `PreToolUse` hook blocks `write_file` calls for every other path. `PostToolUse` and `Stop` write verification evidence under the ignored `.bob/state/` directory.

Run the baseline:

```bash
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s acceptance -v
```

The first command passes. The second fails until `app.release_policy.recommended_upgrade()` exists.

