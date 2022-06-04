
#define NUMBTHREAD 5
#define MAXJOB 100000
#define MAXSIZE 10000000
#define MAXSTOPNAME 200
#define MAXSTOPSNUMB 5200
#define MAXORDERSNUMB 2000
#define MAXCABSNUMB 3000
#define MAXKEYLEN 100
#define MAXINPOOL 4
#define MAXORDID MAXINPOOL*2
#define MAXANGLE 120
#define MAXLEVSIZE 1200000
#define MAXNODE MAXINPOOL+MAXINPOOL-1
#define MAXJSON 50000
#define MAXTHREADMEM 2500000

#define true 1
#define false 0
typedef int boolean;

int getRand(int, int);
void initDistance(void);
void findPool(int, int, char*);
void setCosts(int size);
void genDemand(int size);
void showtime(char *label);

struct Stop {
    int id;
    //char name[MAXSTOPNAME];
    short bearing;
    double longitude;
    double latitude;
};
typedef struct Stop Stop;

struct Order {
    int id;
    short fromStand;
    short toStand;
    short maxWait;
    short maxLoss;
    short distance;
};
typedef struct Order Order;

struct Cab {
    int id;
    short location;
};
typedef struct Cab Cab;

enum FileType {
    STOPS, ORDERS, CABS, CONFIG
};

struct Branch {
  char key[MAXKEYLEN]; // used to remove duplicates and search in hashmap
  short cost;
  unsigned char outs; // BYTE, number of OUT nodes, so that we can guarantee enough IN nodes
  short ordNumb; // it is in fact ord number *2; length of vectors below - INs & OUTs
  int ordIDs[MAXORDID]; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing
  char ordActions[MAXORDID];
  int ordIDsSorted[MAXORDID]; 
  char ordActionsSorted[MAXORDID];
  int cab;
};

typedef struct Branch Branch;

void saveInJson(char *json, Branch *ptr);