define stream s1.

procedure slintschema:
  define input parameter ipSLintDir as character no-undo.

  define variable cnt as integer no-undo.
  define variable cnt2 as integer no-undo.
  define variable dbList  as character no-undo.
  define variable aliases as character no-undo.
  define variable tmp as character no-undo.

  do cnt = 1 to num-dbs:
    if dbtype(cnt) = "PROGRESS" then do:
      create alias 'dictdb' for database value(ldbname(cnt)).
      run value(replace(this-procedure:file-name, "slintsch.p","") + "sch.p") (substitute("&1~\&2.schema", ipSLintDir, ldbname(cnt))).
      assign dbList = dbList + (if dbList eq '' then '' else ',') + substitute("&1/&2.schema", ipSLintDir, ldbname(cnt)).
      tmp = "".
      do cnt2 = 1 to num-aliases:
        if alias(cnt2) ne 'dictdb' and alias(cnt2) ne 'OEIDE_DICTDB' and ldbname(alias(cnt2)) = ldbname(cnt) then 
          tmp = tmp + (if tmp eq '' then '' else ',') + alias(cnt2).
      end.
      if (tmp > '') then
        assign aliases = aliases + (if aliases eq '' then '' else ';') + ldbname(cnt) + ',' + tmp.
    end.
  end.

  output stream s1 to value(ipSLintDir + "~\dblist.txt").
  put stream s1 unformatted string(interval(now, datetime(1, 1, 1970, 0, 0), "milliseconds")) skip.
  put stream s1 unformatted dbList skip.
  put stream s1 unformatted aliases skip.
  output stream s1 close.

end procedure.
