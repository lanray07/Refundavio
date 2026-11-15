$ErrorActionPreference = 'Stop'
$res = Join-Path $PSScriptRoot '../app/src/main/res'
function Read-Resources($folder) {
    [xml]$doc = [IO.File]::ReadAllText((Join-Path $res "$folder/strings.xml"))
    return $doc.resources
}
function Placeholders([string]$value) {
    return (([regex]::Matches($value, '%(?:[0-9]+\$)?[sdf]') | ForEach-Object Value | Sort-Object) -join ',')
}
function Map($elements) {
    $map = @{}
    foreach ($element in $elements) {
        if ($map.ContainsKey($element.name)) { throw "Duplicate key: $($element.name)" }
        $map[$element.name] = $element
    }
    return $map
}
$base = Read-Resources 'values'
$baseStrings = Map $base.string
$basePlurals = Map $base.plurals
foreach ($folder in @('values-es', 'values-fr', 'values-ar')) {
    $current = Read-Resources $folder
    $strings = Map $current.string
    $plurals = Map $current.plurals
    if ($strings.Count -ne $baseStrings.Count -or $plurals.Count -ne $basePlurals.Count) {
        throw "$folder has a different resource count"
    }
    foreach ($key in $baseStrings.Keys) {
        if (!$strings.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($strings[$key].InnerText) -or
            (Placeholders $strings[$key].InnerText) -ne (Placeholders $baseStrings[$key].InnerText)) {
            throw "$folder invalid string: $key"
        }
    }
    foreach ($key in $basePlurals.Keys) {
        if (!$plurals.ContainsKey($key) -or 'other' -notin $plurals[$key].item.quantity) {
            throw "$folder missing plural: $key"
        }
        foreach ($form in $plurals[$key].item) {
            if ((Placeholders $form.InnerText) -ne (Placeholders ($basePlurals[$key].item | Where-Object quantity -eq 'other').InnerText)) {
                throw "$folder invalid plural placeholder: $key/$($form.quantity)"
            }
        }
    }
    Write-Output "$folder : $($strings.Count) strings, $($plurals.Count) plural groups"
}
