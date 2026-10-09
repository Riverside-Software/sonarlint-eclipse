# OpenEdge fork of sonarlint-eclipse

This repository is Riverside Software's fork of SonarSource's sonarlint-eclipse.

- `origin` = Riverside-Software fork, `upstream` = SonarSource
- One branch per upstream release: `OpenEdge-x.y` (e.g. `OpenEdge-12.6`)
- Depends on the matching fork of sonarlint-core (`../sonarlint-core`, branches `OpenEdge-x.y`, versions `x.y.99001`)

## Merging new upstream releases

Do this for each upstream tag (newer than the latest `OpenEdge-x.y` branch) that has not been merged yet. **Never push** unless explicitly asked.

1. `git fetch upstream --tags`, then list tags with `git tag --sort=v:refname`.
   - Only one branch per `x.y`: when several patch tags exist (`6.0.0`, `6.0.1`, `6.0.2`), merge only the latest one.
2. Create the branch from the previous OpenEdge branch:
   `git checkout -b OpenEdge-x.y OpenEdge-<previous>`
3. Merge the tag: `git merge --no-ff --no-edit <tag>` (message: `Merge tag '<tag>' into OpenEdge-x.y`).
4. Resolve conflicts:
   - `MANIFEST.MF` bundle-version attribute: take the tag's side (e.g. `x.y.z.qualifier`). They are changed in the following commits.
   - `pom.xml` project `<version>`, `sonarlint-java-client-osgi` version and `<sloop.version>`: take the tag's side (e.g. `x.y-SNAPSHOT`, upstream SLCore version). They are changed in the following commits.
   - Other upstream property changes (analyzer versions, removed properties...): take the tag's side.
   - Always keep the OpenEdge customizations:
     - `<sonar.oe.version>` property, `sonar-openedge-plugin` artifactItem (`sonaroe.jar`)
     - text and omnisharp artifactItems commented out
     - `<distributionManagement>` pointing to `nexus.rssw.eu`
   - `EnabledLanguages.java`: keep the fork side (only `OPENEDGE` / `OPENEDGE_DB` standalone, `CONNECTED_ADDITIONAL_LANGUAGES = EnumSet.noneOf(...)`), ignore upstream language additions.
   - Sanity check: `git diff <tag> --stat` should only show the usual OpenEdge delta (~19 files).
5. Commit `SLCore a.b.99001`: set `<sonarlint.core.version>` to `a.b.99001`, where `a.b` is the major.minor of the SLCore version used by the tag. Check that `../sonarlint-core` has branch `OpenEdge-a.b` with version `a.b.99001`.
6. Commit `Version x.y.99001`: set the project `<version>` to `x.y.99001` and Bundle-Version of all MANIFEST.MF tags (also for patch tags: `6.0.2-SNAPSHOT` becomes `6.0.99001`). The patch version number must be increased by 100 (e.g. 12.5.7601 becomes 12.6.7701 then 12.7.7801).

Each of steps 5 and 6 is a separate commit that changes only one line of `pom.xml`.
