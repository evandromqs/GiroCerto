import { defineConfig } from "@neon/config/v1";

export default defineConfig({
  auth: true,
  functions: {
    backups: { name: "Giro Certo cloud backups", source: "./backend/cloud-backups.ts" },
  },
  // Branch policy: per-branch tuning
  branch: (branch) => {
    if (branch.isDefault) {
      // Default branch: no overrides, uses project defaults
      return {};
    }
    if (!branch.exists) {
      // New non-default branches: auto-expire
      // Run `neon checkout <name>` to create a new branch with these settings
      return { ttl: "7d" };
    }
    // Existing branch: no changes
    return {};
  },
});
