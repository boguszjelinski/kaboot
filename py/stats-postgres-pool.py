import psycopg2
import time

def execSql(cursor, fil, sql, column):
    cursor.execute(sql)
    rows = cursor.fetchall()
    for row in rows:
        print(row[column], end = ', ', file=fil, flush=True)
    print('', file=fil, flush=True)

try:
    countArr = [0 for i in range(20)] 
    conn = psycopg2.connect("dbname=kabina user=kabina password=kaboot")
    cur = conn.cursor()
    fil = open('C:\\Users\\dell\\TAXI\\GITLAB\\poolstats.txt', 'w')
    cur.execute("select route_id from taxi_order where route_id is not null order by route_id")
    rows = cur.fetchall()
    previous = -1
    count = 0
    for row in rows:
        if previous == row[0]: # the same route
            count += 1
        else: # new route, let's count its passengers, but first save the previous route
            countArr[count] += 1
            count = 1
            previous = row[0]

    for i in range(20):
        print(i, ": ", countArr[i], file=fil)
    fil.close()
except (Exception, psycopg2.DatabaseError) as error:
    print(error)
finally:
    if conn is not None:
        conn.close()
