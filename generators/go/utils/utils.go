package utils

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"kabina/model"
	"math"
	"net/http"
	"strconv"
	"time"
)

const address string = "localhost"
var client = &http.Client{ Timeout: time.Second * 10 }

func GetCab(usr string, cab int) (model.Cab, error) {
	var result model.Cab
	body, err := SendReq(usr, "http://" + address + "/cabs/" + strconv.Itoa(cab), "GET", nil)
	if err != nil {
		return result, err
	}
	if err := json.Unmarshal(body, &result); err != nil { // Parse []byte to the go struct pointer
		fmt.Println("Can not unmarshal Cab")
		return result, err
	}
	return result, nil
}

func GetStops(usr string) ([]model.Stop, error) {
	var result []model.Stop
	body, err := SendReq(usr, "http://" + address + "/stops", "GET", nil)
	if err != nil {
		return result, err
	}
	if err := json.Unmarshal(body, &result); err != nil { // Parse []byte to the go struct pointer
		fmt.Println("Can not unmarshal Stops")
		return result, err
	}
	return result, nil
}

func GetRoute(usr string) (model.Route, error) {
	var result model.Route
	body, err := SendReq(usr, "http://" + address + "/routes", "GET", nil)
	if err != nil {
		return result, err
	}
	if err := json.Unmarshal(body, &result); err != nil {
		//fmt.Println("Can not unmarshal Route")
		return result, err
	}
	return result, nil
}

func UpdateCab(usr string, cab_id int, stand int, status string) {
	values := map[string]string{"location": strconv.Itoa(stand), "status": status}
    json_data, err := json.Marshal(values)
	if err != nil {
		fmt.Print(err.Error())
		return
	}
	_, err = SendReq(usr, "http://" + address + "/cabs/" + strconv.Itoa(cab_id), 
						 "PUT", bytes.NewReader(json_data))
	if err != nil {
		fmt.Print(err.Error())
		return
	}
	//fmt.Println(body)
}

func UpdateStatus(usr string, url string, id int, status string) {
	values := map[string]string{"status": status}
	//fmt.Printf("Update %s %d status %s\n", url, id, status);
	json_data, err := json.Marshal(values)
	if err != nil {
		fmt.Print(err.Error())
		return
	}
	_, err = SendReq(usr, "http://" + address + "/" + url+ "/" + strconv.Itoa(id), 
						 "PUT", bytes.NewReader(json_data))
	if err != nil {
		fmt.Print(err.Error())
		return
	}
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