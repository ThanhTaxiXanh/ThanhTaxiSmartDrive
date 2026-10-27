import csv
import hashlib
import importlib.util
from pathlib import Path
import sqlite3
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'build_offline_pois.py'
spec = importlib.util.spec_from_file_location('poi_builder', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class OfflinePoiPackTest(unittest.TestCase):
    def test_accentless_prefix_lookup_and_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            csv_file = base / 'source.csv'
            with csv_file.open('w',encoding='utf-8',newline='') as output:
                writer = csv.writer(output)
                writer.writerow(module.FIELDS)
                writer.writerow(['demo_01','Bến xe Giáp Bát','Đường Giải Phóng','Hà Nội',
                    'BUS_STATION','20.98','105.84','Ben xe Giap Bat','TEST ONLY','false'])
            db_file = base / 'places.db'
            meta = module.build(csv_file,db_file,'Internal test source','Synthetic test')
            self.assertEqual(meta['record_count'],1)
            self.assertEqual(hashlib.sha256(db_file.read_bytes()).hexdigest(),meta['sha256'])
            conn = sqlite3.connect(db_file)
            self.assertEqual(conn.execute('PRAGMA integrity_check').fetchone()[0],'ok')
            self.assertEqual(conn.execute('SELECT count(*) FROM poi_geo').fetchone()[0],1)
            self.assertEqual(conn.execute("""SELECT p.id FROM pois p JOIN pois_fts
                ON pois_fts.rowid=p.rowid WHERE pois_fts MATCH ?""",
                ('"ben"* AND "xe"* AND "giap"*',)).fetchone()[0],'demo_01')
            conn.close()

    def test_invalid_coordinates_preserve_previous_pack(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp); csv_file = base/'invalid.csv'; db_file = base/'places.db'
            with csv_file.open('w',encoding='utf-8',newline='') as output:
                writer=csv.writer(output); writer.writerow(module.FIELDS)
                writer.writerow(['id','Sai','address','province','PARK','nan','100','','SRC','false'])
            db_file.write_bytes(b'keep previous pack')
            with self.assertRaises(ValueError):
                module.build(csv_file,db_file,'Test license','Synthetic test')
            self.assertEqual(db_file.read_bytes(),b'keep previous pack')
            self.assertFalse((base/'places.db.building').exists())

if __name__=='__main__':
    unittest.main()
