#!/usr/bin/env python3
"""Compile *user-provided and licensed* CSV into an Android FTS5 + RTree POI pack."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import sqlite3
import unicodedata

FIELDS = ('id','name','address','province','category','lat','lon','aliases','source','verified')

def normalize(s):
    s = s.lower().replace('đ', 'd')
    s = ''.join(c for c in unicodedata.normalize('NFD', s) if not unicodedata.combining(c))
    return ' '.join(''.join(c if c.isascii() and c.isalnum() else ' ' for c in s).split())

def build(csv_path: Path, out: Path, license_name: str, name: str):
    if not license_name.strip() or not name.strip():
        raise ValueError('Pack name and source-license text required')
    out.parent.mkdir(parents=True, exist_ok=True)
    pending = out.with_name(out.name + '.building')
    if pending.exists():
        pending.unlink()
    db = sqlite3.connect(pending)
    count = 0
    try:
        db.executescript("""
          PRAGMA journal_mode=DELETE;
          PRAGMA user_version=1;
          CREATE TABLE pois (rowid INTEGER PRIMARY KEY, id TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL, address TEXT NOT NULL, province TEXT NOT NULL,
            category TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
            aliases TEXT NOT NULL, source TEXT NOT NULL,
            verified INTEGER NOT NULL CHECK (verified IN (0,1)),
            name_norm TEXT NOT NULL, terms_norm TEXT NOT NULL);
          CREATE VIRTUAL TABLE pois_fts USING fts5(name_norm, terms_norm,
            content='pois',content_rowid='rowid',tokenize='unicode61 remove_diacritics 2',prefix='2 3 4');
          CREATE VIRTUAL TABLE poi_geo USING rtree(id,min_lat,max_lat,min_lon,max_lon);
        """)
        with csv_path.open(encoding='utf-8-sig', newline='') as stream:
            reader = csv.DictReader(stream)
            if not reader.fieldnames or any(f not in reader.fieldnames for f in FIELDS):
                raise ValueError('CSV requires: ' + ','.join(FIELDS))
            with db:
                for row in reader:
                    count += 1
                    if count > 1_000_000: raise ValueError('More than 1 million POIs')
                    ident = (row['id'] or '').strip()
                    label = (row['name'] or '').strip()
                    source = (row['source'] or '').strip()
                    if not ident or not label or not source:
                        raise ValueError('Missing id/name/source at row ' + str(count))
                    lat, lon = float(row['lat']), float(row['lon'])
                    if not (math.isfinite(lat) and math.isfinite(lon)
                            and -90 <= lat <= 90 and -180 <= lon <= 180):
                        raise ValueError('Invalid coordinates: ' + ident)
                    verified = (row['verified'] or '').lower().strip()
                    if verified not in ('true','false','1','0'):
                        raise ValueError('Invalid verified flag: ' + ident)
                    fields = [(row[k] or '').strip() for k in FIELDS]
                    norm_name = normalize(label)
                    terms = normalize(' '.join([label,fields[2],fields[3],fields[7]]))
                    cur = db.execute("""INSERT INTO pois(id,name,address,province,category,lat,lon,
                         aliases,source,verified,name_norm,terms_norm)
                         VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
                         (ident,label,fields[2],fields[3],fields[4],lat,lon,fields[7],source,
                          int(verified in ('true','1')),norm_name,terms))
                    rid = cur.lastrowid
                    db.execute('INSERT INTO pois_fts(rowid,name_norm,terms_norm) VALUES(?,?,?)',
                               (rid,norm_name,terms))
                    db.execute('INSERT INTO poi_geo VALUES(?,?,?,?,?)',(rid,lat,lat,lon,lon))
        if count < 1: raise ValueError('Empty POI pack')
        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise ValueError('SQLite integrity failed')
        if db.execute('SELECT count(*) FROM poi_geo').fetchone()[0] != count:
            raise ValueError('Incomplete RTree')
        db.execute('PRAGMA optimize')
    except Exception:
        db.close()
        pending.unlink(missing_ok=True)
        raise
    db.close()
    sha = hashlib.sha256(pending.read_bytes()).hexdigest()
    manifest = {'schema_version':1, 'pack_name':name, 'source_license':license_name,
        'record_count':count,'db_filename':out.name,'sha256':sha,
        'warning':'Supplier data not legally verified; review license and coordinates.'}
    pending.replace(out)
    out.with_name(out.name+'.manifest.json').write_text(
        json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return manifest

if __name__=='__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--csv',required=True,type=Path)
    p.add_argument('--out',required=True,type=Path)
    p.add_argument('--license',required=True)
    p.add_argument('--name',required=True)
    a = p.parse_args()
    print(json.dumps(build(a.csv,a.out,a.license,a.name),ensure_ascii=False))
