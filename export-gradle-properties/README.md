# Grails export-gradle-properties action

Exports `gradle.properties` as environment variables.

## Example usage

```yaml
- name: Export Gradle Properties
  uses: grails/github-actions/export-gradle-properties@master
- name: Update BOM
  uses: grails/github-actions/update-bom@master
  with:
    token: ${{ secrets.GH_TOKEN }}
    branch: master 
    property: someVersion
    version: ${{ env.projectVersion }}
```
