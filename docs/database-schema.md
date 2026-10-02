# Database Schema

Stage 1 starts with Flyway and a minimal `app_metadata` table. Domain tables will be introduced through numbered, forward-only migrations.

Never edit an applied migration. Update this document whenever a migration changes the schema.
