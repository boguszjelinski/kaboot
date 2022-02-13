import psycopg2
import time
from tkinter import * 

id = [-1 for i in range(5000)] 
routeId = [-1 for i in range(5000)] 
place = [-1 for i in range(5000)] 
fromStand = [-1 for i in range(5000)] 
toStand = [-1 for i in range(5000)] 
#
stopId = [-1 for i in range(6000)] 
bearing = [-1 for i in range(6000)] 
longitude = [-1 for i in range(6000)] 
latitude = [-1 for i in range(6000)] 
name = [-1 for i in range(6000)] 
#
min_long = 18.881264 
max_long = 19.357892
min_lat = 47.338022 # select min(latitude) from stop
max_lat = 47.659618
margin = 50
routeIdx = 1
numbStops = 0
numbRoutes = 0
numbLegs = 0

root = Tk() 
root.geometry("2100x1100")
root.title("Route presenter")
canvas = Canvas(root, width=2100, height=1100, bg='white')

def getX(long):
    temp_x = (long - min_long) / (max_long - min_long)
    return margin + (2100 - 2*margin) * temp_x

def getY(lat):
    temp_y = (lat - min_lat) / (max_lat - min_lat)
    return 1000 - margin - (1100 - 2*margin) * temp_y

def draw_circle(x, y, r, cnvs): 
    x0 = x - r
    y0 = y - r
    x1 = x + r
    y1 = y + r
    return cnvs.create_oval(x0, y0, x1, y1)

def onKeyPress(event):
    global routeIdx
    global numbRoutes

    if event.keysym == "Up":
        if routeIdx > 1:
            routeIdx -= 1
    else:
        if routeIdx < numbRoutes - 1: 
            routeIdx += 1
    showRoute(routeIdx)

def showRoute(idx):
    global canvas
    global numbLegs
    global longitude
    global latitude
    global fromStand

    canvas.delete("all")
    place = 0
    found = 0
    for i in range(numbLegs-1):
        if id[i] != idx and found == 1:
            show_circle(toStand[i - 1], place, canvas)
            break
        if id[i] == idx:
            if found == 0:
                found = 1
                canvas.create_text(50, 10, font="Console 10 bold",text=str(routeId[i]))
            show_circle(fromStand[i], place, canvas)
            place += 1

def show_circle(stop, place, canvas):
    print('stop:{0} long:{1} lat:{2}'.format(stop, get_long(stop), get_lat(stop)))
    x = getX(get_long(stop))
    y = getY(get_lat(stop))
    draw_circle(x, y, 3, canvas)
    canvas.create_text(x, y-9, font="Console 10 bold",text=str(place))

def get_long(stop_id):
    for i in range(numbStops-1):
        if stopId[i] == stop_id:
            return longitude[i]

def get_lat(stop_id):
    for i in range(numbStops-1):
        if stopId[i] == stop_id:
            return latitude[i]

try:
    conn = psycopg2.connect("dbname=kabina user=kabina password=kaboot")
    cur = conn.cursor()
    # reading legs
    cur.execute("select route_id, place, from_stand, to_stand FROM leg ORDER BY route_id, place")
    rows = cur.fetchall()
    previous = -1
    idx = 0
    
    for row in rows:
        if previous != row[0]: # not the same route
            numbRoutes += 1 
        id[idx]         = numbRoutes
        routeId[idx]    = row[0]
        place[idx]      = row[1]
        fromStand[idx]  = row[2]
        toStand[idx]    = row[3]
        previous = row[0]
        idx += 1
    
    numbLegs = idx
    
    # reading stops
    cur.execute("select id, bearing, latitude, longitude, name FROM stop")
    rows = cur.fetchall()
    numbStops = 0
    for row in rows:
        stopId[numbStops]    = row[0]
        bearing[numbStops]   = row[1]
        latitude[numbStops]  = row[2]
        longitude[numbStops] = row[3]
        name[numbStops]      = row[4]
        numbStops += 1
    #
    #canvas.create_line(10, 10, 20, 200, fill="#ff0000")
    root.bind('<KeyPress>', onKeyPress)
    showRoute(1)
    canvas.pack()
    root.mainloop() 
except (Exception, psycopg2.DatabaseError) as error:
    print(error)
finally:
    if conn is not None:
        conn.close()
