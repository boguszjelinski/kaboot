package utils

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"kabina/model"
	"math"
	"math/rand"
	"net/http"
	"strconv"
	"time"
)

const host1 string = "192.168.10.176"
const host2 string = "localhost"

var client = &http.Client{ Timeout: time.Second * 30 }

func GetEntity[N model.Cab | model.Demand | model.Route | []model.Stop ](usr string, path string) (N, error) {
	var result N
	body, err := SendReq(usr, "http://" + host1 + path, "GET", nil)
	if err != nil {
		return result, err
	}
	if len(body) == 0 {
		return result, errors.New("Empty body")
	}
	if err := json.Unmarshal(body, &result); err != nil {
		fmt.Println("Can not unmarshal " + path)
		return result, err
	}
	return result, nil
}

func UpdateCab(usr string, cab_id int, stand int, status string) {
	UpdateEntity(usr, "/cabs/", cab_id, map[string]string{"location": strconv.Itoa(stand), "status": status})
}

func UpdateStatus(usr string, path string, id int, status string) {
	UpdateEntity(usr, path, id, map[string]string{"status": status})
}

func UpdateEntity(usr string, path string, id int, values map[string]string) {
    json_data, err := json.Marshal(values)
	if err != nil {
		fmt.Print(err.Error())
		return
	}
	_, err = SendReq(usr, "http://" + host2 + path + strconv.Itoa(id),
						 "PUT", bytes.NewReader(json_data))
	if err != nil {
		fmt.Print(err.Error())
		return
	}
	//fmt.Println(body)
}

func SaveDemand(method string, usr string, dem model.Demand) (model.Demand, error) {
	var result model.Demand
	values := map[string]string{"fromStand": strconv.Itoa(dem.From), 
								"toStand": strconv.Itoa(dem.To), 
								"status": dem.Status,
								"maxWait": strconv.Itoa(dem.MaxWait),
								"maxLoss": strconv.Itoa(dem.MaxLoss),
								"shared": strconv.FormatBool(dem.InPool)}
	json_data, err := json.Marshal(values)
	if err != nil {
		fmt.Print(err.Error())
		return result, err
	}
	url := "http://" + host2 + "/orders/";
	if method == "PUT" {
		url += strconv.Itoa(dem.Id)
	}
	body, err := SendReq(usr, url, method, bytes.NewReader(json_data))
	if err != nil {
		fmt.Print(err.Error())
		return result, err
	}
	if len(body) == 0 {
		return result, errors.New("Empty body")
	}
	if method == "PUT" {
		return result, nil // just empty body
	}
	if err := json.Unmarshal(body, &result); err != nil {
		fmt.Printf("Can not unmarshal taxi order, usr=%s, from=%d to=%d\n", usr, dem.From, dem.To)
		return result, err
	}
	return result, nil
}

func SendReq(usr string, url string, method string, body io.Reader ) ([]byte, error) {
	req, err := http.NewRequest(method, url, body)
	if err != nil {
		fmt.Print(err.Error())
		return nil, err
		//os.Exit(1)
	}
	req.SetBasicAuth(usr, usr)
	req.Header.Set("Content-Type", "application/json") // for POST 
	resp, err := client.Do(req)
	if err != nil {
		fmt.Print(err.Error())
		return nil, err
	}
	defer resp.Body.Close()
	respBody, err := ioutil.ReadAll(resp.Body) // response body is []byte
	if err != nil {
		fmt.Print(err.Error())
		return nil, err
	}
	return respBody, nil
}

//================ BEGIN: DISTANCE SERVICE ==============
func GetDistance(stops *[]model.Stop, from_id int, to_id int) int {
	var from = -1
	var to = -1
	for x :=0; x<len((*stops)); x++ {
		if (*stops)[x].Id == from_id {
			from = x
			break
		}
	}
	for x :=0; x<len((*stops)); x++ {
		if (*stops)[x].Id == to_id {
			to = x
			break
		}
	}
	if from == -1 || to == -1 {
		fmt.Printf("from %d or to %d ID not found in stops", from_id, to_id)
		return -1
	}
	return int (Dist((*stops)[from].Latitude, (*stops)[from].Longitude, 
					 (*stops)[to].Latitude, (*stops)[to].Longitude));
}

// https://dzone.com/articles/distance-calculation-using-3
func Dist(lat1 float64, lon1 float64, lat2 float64, lon2 float64) float64 {
	var theta = lon1 - lon2;
	var dist = math.Sin(deg2rad(lat1)) * math.Sin(deg2rad(lat2)) + math.Cos(deg2rad(lat1)) * math.Cos(deg2rad(lat2)) * math.Cos(deg2rad(theta));
	dist = math.Acos(dist);
	dist = rad2deg(dist);
	dist = dist * 60 * 1.1515;
	dist = dist * 1.609344;
	return (dist);
}

func deg2rad(deg float64) float64 {
	return (deg * math.Pi / 180.0);
}

func rad2deg(rad float64) float64 {
	return (rad * 180.0 / math.Pi);
}
//================ END: DISTANCE SERVICE ==============
const MAX_TRIP = 4; // this should not have any impact, distance not based on ID any more, but maybe it will help a bit

func RandomTo(from int, maxStand int) int {
	diff := rand.Intn(MAX_TRIP * 2) - MAX_TRIP;
	if diff == 0 { diff = 1 }
	to := 0;
	if from + diff > maxStand -1 { 
		to = from - diff 
	} else if from + diff < 0 { 
		to = 0 
	} else { to = from + diff; }
	return to;
}