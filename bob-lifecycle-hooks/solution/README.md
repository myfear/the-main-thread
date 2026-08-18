# Reference Solution

This directory contains the expected application behavior after the `/upgrade-plan` task. The lifecycle-hook configuration stays in `demo/.bob/`; only the allowed application and unit-test files change.

Run the reference solution from this directory:

```bash
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s acceptance -v
```

