# Grails github-actions

Custom GitHub actions used by the Grails team.

## Releasing a new version

To release a new version, you need to create a new tag with the version number (prefixed with v). This can be done via
the GitHub Releases page or via the command line.
```console
git tag v3.0
git push origin v3.0
```

To make major version references work, create a new branch with the major version number (if it does not already exist).
```console
git checkout -b v3 v3.0
git push origin v3
```

When a new minor version is released, update the major version branch to point to the new release tag.
```console
git checkout v3
git reset --hard v3.1
git push --force origin v3
```

## Usages
Used by: https://github.com/search?q=org%3Agrails+%22uses%3A+grails%2Fgithub-actions%2F%22+language%3Ayml&type=code
