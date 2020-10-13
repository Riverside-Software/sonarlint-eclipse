define input parameter ipFile as character no-undo.

define variable idxStr as character no-undo.
define variable idxCnt as integer   no-undo.

define stream s1.
output stream s1 to value(ipFile).

for each dictdb._file where dictdb._file._file-number > 0 and dictdb._file._file-number < 32768 no-lock by _file._file-name:
  put stream s1 unformatted "T" + _file._file-name skip.
  for each dictdb._field where dictdb._field._file-recid = recid(dictdb._file) no-lock:
    put stream s1 unformatted "F" + _field._field-name + ":" + _field._data-type + ":" + string(_field._extent) skip.
  end.
  for each dictdb._index where dictdb._index._file-recid = recid(dictdb._file) no-lock:
    assign idxStr = "I" + _index._index-name + ":" + (if _file._prime-index = recid(_index) then 'P' else '') + (if _index._unique then 'U' else '')
           idxCnt = 0.
    for each dictdb._index-field where dictdb._index-field._index-recid = recid(dictdb._index) no-lock:
      find dictdb._field where _index-field._field-recid = recid(dictdb._field) no-lock.
      if (_field._field-name gt '') then idxcnt = idxcnt + 1.
      assign 
             idxStr = idxStr + ':' + (if _index-field._ascending then 'A' else 'D') + _field._field-name.
    end.
    if (idxCnt > 0) then
      put stream s1 unformatted idxStr skip.
  end.
  
end.

output stream s1 close.
