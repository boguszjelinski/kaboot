import mariadb
import time

def execSql(cursor, sql, column):
    cursor.execute(sql)
    rows = cursor.fetchall()
    for row in rows:
        print(row[column], end = ', ')

try:
    conn = mariadb.connect(
        user="kabina",
        password="kaboot",
        host="localhost",
        port=3306,
        database="kabina")
    cur = conn.cursor()
    # column names
  
    print('')
    for t in range(0,180):
          # first stats
        execSql(cur, 'select * from stat order by name', 0)
        execSql(cur, 'select status, count(*) from cab group by status order by status', 0)
        execSql(cur, 'select status, count(*) from taxi_order group by status order by status', 0)
        
        print('') # new line
        execSql(cur, 'select * from stat order by name', 2)
        execSql(cur, 'select status, count(*) from cab group by status order by status', 1)
        execSql(cur, 'select status, count(*) from taxi_order group by status order by status', 1)
        
        print('') # new line
        time.sleep(60) # 60 seconds

except (Exception, psycopg2.DatabaseError) as error:
    print(error)
finally:
    if conn is not None:
        conn.close()
