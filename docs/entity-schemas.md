# Entity schema conventions

HTTP bodies remain JSON:API (`data` / `attributes` / `relationships`). Entity YAML files describe **domain mapping** and **operation dispatch**.

| Extension | Location | Purpose |
|-----------|----------|---------|
| `x-entity` | Entity root | Java class name (`Item`, `Category`) |
| `x-resource-type` | Entity root | JSON:API `data.type` (`items`, `categories`) |
| `x-field` | Property | Domain path (`/Item/name`); last segment = Java field |
| `@id` | Property | Maps to JSON:API `data.id` |
| `@members` | Property | Relationship; wire name = strip `@` (`members`) |
| `x-filterable` / `x-sortable` | Root or property | Query metadata |
| `dependentSchemas.$` | Entity root | Operation branches with `x-service` per `$defs/*-request` |
| `x-entity-schema` | Path operation | Entity file ref (`schemas/items.yaml`) |
| `x-operation` | Path operation | Selects branch (`add-request`, `find-by-id-request`, …) |

Path `x-operation` replaces path-level `x-service` for REST ops. Atomic paths keep `x-atomic-*` on paths.

## Domain vs wire schemas

| Schema | Location | Purpose |
|--------|----------|---------|
| `$defs/add-request` | Entity YAML (hand-authored) | Domain constraints: `required`, excluded properties (`@members: false`) |
| `$defs/jsonapi-add-request` | Generated at build (`generateWireSchemas`) | Explicit JSON:API wire shape for OpenAPI/Swagger |

The Gradle plugin derives wire schemas from domain root properties + operation `$defs`:

- Non-`@` properties → `data.attributes.{name}`
- `@id` → `data.id` (when required by operation)
- `@members` etc. → `data.relationships.{name}` (unless excluded)
- `add-relationships-request` → `{ "data": [ resource-linkage ] }`

Build pipeline: `validateOpenApi` → `generateWireSchemas` → `convertOpenApiToJson` (patches path `requestBody` refs) → `generateJsonApiSupport`.

Served spec (`openapi.json`) references `schemas/{entity}.yaml#/$defs/jsonapi-{operation}` so Swagger UI shows exact request examples.

## Operation key catalog

| Operation key | Typical HTTP | Wire def name |
|---------------|--------------|---------------|
| `add-request` | POST | `jsonapi-add-request` |
| `find-request` | GET collection | `jsonapi-find-request` |
| `find-by-id-request` | GET by id | `jsonapi-find-by-id-request` |
| `update-request` | PATCH | `jsonapi-update-request` |
| `remove-request` | DELETE | `jsonapi-remove-request` |
| `add-relationships-request` | POST relationships | `jsonapi-add-relationships-request` |
| `find-members-request` | GET members | `jsonapi-find-members-request` |

## Example entity file

```yaml
$schema: https://json-schema.org/draft/2020-12/schema
type: object
x-entity: Item
x-resource-type: items
properties:
  '@id':
    type: string
    x-field: /Item/id
  name:
    type: string
    x-field: /Item/name
dependentSchemas:
  $:
    anyOf:
      - $ref: '#/$defs/find-request'
        x-service: itemService.findAll
      - $ref: '#/$defs/add-request'
        x-service: itemService.create
$defs:
  find-request:
    type: object
  add-request:
    type: object
    required: [name]
```

## Members extension

For `find-members-response`, add `x-member-entity-schema` on the operation `$defs` branch to point at the member entity YAML. The plugin generates member-aware wire schemas for collection responses.
