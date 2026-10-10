$V = '([A-Za-z_][\w.]*\([^()]*\)|"[^"\n]*"|[^,\n)]+)'
foreach ($f in (Get-ChildItem -Recurse -Filter *.kt)) {
    $t = [IO.File]::ReadAllText($f.FullName); $o = $t
    $t = [regex]::Replace($t, 'TimerSetting\(\s*true\s*,\s*([^)]+)\)\.intervalTicks', 'ScriptTrigger(TriggerKind.TIMER, intervalSeconds = $1).intervalTicks()')
    $t = [regex]::Replace($t, 'timer\s*=\s*TimerSetting\(\s*enabled\s*=\s*true\s*,\s*intervalSeconds\s*=\s*([^,)]+)\)', 'trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = $1)')
    $t = [regex]::Replace($t, 'timer\s*=\s*TimerSetting\(\s*true\s*,\s*([^,)]+)\)', 'trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = $1)')
    $t = [regex]::Replace($t, ',\s*timer\s*=\s*TimerSetting\(\s*false[^)]*\)', '')
    $t = [regex]::Replace($t, ',\s*timer\s*=\s*TimerSetting\(\)', '')
    $t = [regex]::Replace($t, ',\s*activation\s*=\s*(me\.[\w.]+\.)?ActivationMode\.[A-Z_]+', '')
    foreach ($n in 'owner','listed','contentModified','collapsedForStartIds','collapsedConditionIds','dsl') {
        $t = [regex]::Replace($t, ",\s*$n\s*=\s*$V", '')
        $t = [regex]::Replace($t, "\(\s*$n\s*=\s*$V\s*,\s*", '(')
        $t = [regex]::Replace($t, "(?m)^\s*$n\s*=\s*$V,?\s*\r?\n", '')
    }
    $t = [regex]::Replace($t, 'createPlacement\(\s*[^,\n(]+,\s*', 'createPlacement(')
    $t = [regex]::Replace($t, "(?m)^import me\.awabi2048\.kantancommander\.model\.(ActivationMode|TimerSetting)\r?\n", '')
    if ($t -match 'ScriptTrigger\(' -and $t -notmatch 'import me\.awabi2048\.kantancommander\.model\.ScriptTrigger') {
        $t = [regex]::Replace($t, '(?m)^(package [^\r\n]+\r?\n)', "`$1`r`nimport me.awabi2048.kantancommander.model.ScriptTrigger")
    }
    if ($t -match 'TriggerKind\.' -and $t -notmatch 'import me\.awabi2048\.kantancommander\.model\.TriggerKind') {
        $t = [regex]::Replace($t, '(?m)^(package [^\r\n]+\r?\n)', "`$1`r`nimport me.awabi2048.kantancommander.model.TriggerKind")
    }
    if ($t -cne $o) { [IO.File]::WriteAllText($f.FullName, $t) }
}
