export function clearFromEmpty(object) {
    return Object.entries(object)
        .filter(([key, value]) => value != null && value !== "")
        .reduce((acc, [key, value]) => ({...acc, [key]: value}), {})
}

export function clearFromDefaults(object, defaults) {
    return Object.entries(object)
        .filter(([key, value]) => value !== defaults[key])
        .reduce((acc, [key, value]) => ({...acc, [key]: value}), {})
}
