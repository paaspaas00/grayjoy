import type { LocalCache, State, PluginSettings } from "./types.ts"

let local_storage_cache: LocalCache
let local_state: State
let local_settings: PluginSettings

export function get_local_storage_cache(): LocalCache { return local_storage_cache }
export function set_local_storage_cache(value: LocalCache): void { local_storage_cache = value }

export function get_local_state(): State { return local_state }
export function set_local_state(value: State): void { local_state = value }

export function get_local_settings(): PluginSettings { return local_settings }
export function set_local_settings(value: PluginSettings): void { local_settings = value }
