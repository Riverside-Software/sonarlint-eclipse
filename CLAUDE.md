# OpenEdge fork of sonarlint-eclipse

This repository is Riverside Software's fork of SonarSource's sonarlint-eclipse (branded "CABL - SonarLint Eclipse").

- `origin` = Riverside-Software fork, `upstream` = SonarSource
- One branch per upstream release: `OpenEdge-x.y` (e.g. `OpenEdge-12.6`)
- Depends on the matching fork of sonarlint-core (`../sonarlint-core`, branches `OpenEdge-a.b`, versions `a.b.99001`)

## Tags

- Upstream release tags look like `12.7.0.84850` (`x.y.z.build`). Only these are merged.
- `vX.Y.NNNN` tags (e.g. `v12.6.7702`) are fork release tags. `old/*` and other tags are not relevant.

## Merging new upstream releases

Merge every upstream release tag newer than the latest `OpenEdge-x.y` branch, oldest first, each one on its own branch created from the previous one. **Never push** unless explicitly asked.

1. `git fetch upstream --tags`, then list release tags with `git tag --sort=v:refname | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'`.
   - Only one branch per `x.y`: when several patch tags exist (`6.0.0.x`, `6.0.1.x`, `6.0.2.x`), merge only the latest one.
2. Create the branch from the previous OpenEdge branch:
   `git checkout -b OpenEdge-x.y OpenEdge-<previous>`
3. Merge the tag: `git merge --no-ff --no-edit <tag>` (default message: `Merge tag '<tag>' into OpenEdge-x.y`).
4. Resolve conflicts, then `git commit --no-edit`:
   - **Version lines** — take the tag's side (they are fixed in steps 5 and 6):
     - `Bundle-Version` in every `META-INF/MANIFEST.MF` and `version=` in `org.sonarlint.eclipse.feature/feature.xml` (`x.y.z.qualifier`)
     - project/parent `<version>` in every `pom.xml` (`x.y.z-SNAPSHOT`)
     - `<sloop.version>` in root `pom.xml` and `sonarlint-java-client-osgi` `<version>` in `target-platforms/commons-build.target` (upstream SLCore version, e.g. `11.9.0.86162`)
   - **`org.sonarlint.eclipse.core/pom.xml` artifactItems** — keep the fork side: the `eu.rssw.sonar.openedge` `sonar-openedge-plugin` and `progress-rules-plugin` artifactItems replace upstream's java / javascript / php / python / html / xml analyzers. Ignore upstream analyzer version bumps there.
   - **Language sets** — keep the fork side, ignore upstream language additions:
     - `SonarLintUtils.java`: `DEFAULT_LANGUAGES = SECRETS, OPENEDGE, OPENEDGE_DB`; `OPTIONAL_LANGUAGES` and `DEFAULT_CONNECTED_LANGUAGES` = `EnumSet.noneOf(...)`
     - `SonarLintLanguage.java`: `OPENEDGE` / `OPENEDGE_DB` entries
   - **Files deleted upstream but modified in the fork** (e.g. an old `target-platforms/*.target`): accept the deletion (`git rm`) if the fork change was only a repository URL swap (repox.jfrog.io → download.eclipse.org).
   - Other upstream changes: take the tag's side.
   - Fork customizations that must survive (usually merge cleanly):
     - `org.sonarlint.eclipse.pdt` module (OpenEdge project configurator), listed in root `pom.xml` modules and `feature.xml`
     - only `windows.x64` and `no-arch` sloop fragments (other platforms removed from root `pom.xml` modules and `feature.xml`)
     - `SonarLintBackendService.java`: JS/HTML/XML embedded plugins commented out, "CABL - SonarLint Eclipse" client name / `eclipse-cabl` telemetry
     - root `pom.xml`: `PDS-plugins` / `PDS-dependencies` p2 repositories, `sonar-maven-plugin`, SCA options
     - `org.sonarlint.eclipse.site/pom.xml` signing with `eToken.cfg` and `KEYSTORE_*` env vars
     - `Jenkinsfile`, `unsigned.Jenkinsfile`, `build.bat`, `eToken.cfg`, `CLAUDE.md`
     - repox.jfrog.io → download.eclipse.org URLs in `target-platforms/*.target`
   - Sanity checks:
     - No conflict markers left: `git grep -n '^<<<<<<<'`.
     - The file list of `git diff <tag> --name-only` (after the merge commit) must match the one of `git diff <previous-tag> OpenEdge-<previous>~2 --name-only` (the previous merge commit), apart from upstream-deleted files. Currently ~40 files.
5. Commit `SLCore a.b.99001`, where `a.b` is the major.minor of the upstream SLCore version (e.g. `11.9.0.86162` → `11.9.99001`). Change exactly two lines:
   - `<sloop.version>` in root `pom.xml`
   - `sonarlint-java-client-osgi` `<version>` in `target-platforms/commons-build.target`

   First check that `../sonarlint-core` has branch `OpenEdge-a.b` whose project version is `a.b.99001` (`git -C ../sonarlint-core show OpenEdge-a.b:pom.xml | grep -m2 '<version>'`). If not, stop and ask.
6. Commit `Version x.y.NNNN`: replace every `x.y.z-SNAPSHOT`, `x.y.z.qualifier` and old fork version (e.g. `12.6.7702`, also used by the fork-only `org.sonarlint.eclipse.pdt` module) with `x.y.NNNN` in all `pom.xml`, `META-INF/MANIFEST.MF` and `feature.xml` files. One line per file, 32 files currently.
   - `NNNN` = first fork version of the previous branch + 100. The fork's own patch counter starts again at `01` (e.g. `12.5.7601` → `12.6.7701`; `12.6.7702` → `12.7.7801` → `12.8.7901` → `13.0.8001`).

Commit messages for steps 5 and 6 are only the title (`SLCore 11.9.99001`, `Version 12.7.7801`), plus the attribution trailer.
