#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <signal.h>
#include "dynapool.h"

//			"-LC:\\msys64\\mingw64\\x86_64-w64-mingw32\\lib\\postgres",
//			"-lpq"
//			"-IC:\\msys64\\mingw64\\include\\postgres",
// MAC: cc -Wno-implicit-function-declaration poold.c distsrvc.c dynapool.c -w -o poold
extern short distance[MAXSTOPSNUMB][MAXSTOPSNUMB];

time_t rawtime;
struct tm * timeinfo;

char *stopsFileName, *ordersFileName, *cabsFileName, *outFileName, *flagFileName, *exitFileName;
char json[MAXJSON];
int numbThreads = 4;
int maxInPool[3]= {121, 410, 600};
int inPool[3]= {4, 3, 2};

Stop stops[MAXSTOPSNUMB];
int stopsNumb;

Order demand[MAXORDERSNUMB];
Order demandTemp[MAXORDERSNUMB];
int demandSize;

Cab supply[MAXCABSNUMB];
int cabsNumb;

int memSize[MAXNODE] = {5000000, 9000000, 9000000, 14000000, 12000000, 1500000, 50000};
Branch *node[MAXNODE];
int nodeSize[MAXNODE];
int nodeSizeSMP[NUMBTHREAD];

struct arg_struct {
   int i;
   int chunk;
   int lev;
   int inPool;
} *args[NUMBTHREAD];

volatile sig_atomic_t done = 0;

void handle_signal(int signum)
{
   done = 1;
}

void initMem() {
  for (int i=0; i<MAXNODE; i++)
    node[i] = malloc(sizeof(Branch) * memSize[i]);
  for (int i = 0; i<NUMBTHREAD; i++)
    args[i] = malloc(sizeof(struct arg_struct) * 1);
}

void freeMem() {
  for (int i=0; i<MAXNODE; i++) {
    free(node[i]);
    nodeSize[i] = 0;
  }
  for (int i=0; i<NUMBTHREAD; i++) {
    nodeSizeSMP[i] = 0;
    free(args[i]);
  }
}

void showtime(char *label) {
    time(&rawtime);
    timeinfo = localtime(&rawtime);
    printf("%s: %s", label, asctime(timeinfo));
}

double str2double(char str[]) 
{
    double d;
    sscanf(str, "%lf", &d);
    return d;
}

void parse(char line[], int recNumb, enum FileType type) 
{   char val[200]; // the longest field
    for (int i=0, count=0, strStart=0; i<strlen(line); i++) 
        if (line[i] == ',' || line[i] == '\n' || line[i] == '\r') {
            int j = 0;
            for (; strStart + j < i; j++) val[j] = line[strStart + j];
            val[j] = 0;
            if (type == STOPS) 
                switch (count) {
                    case 0: stops[recNumb].id = atoi(val); break;
                    //case 2: strcpy(stops[recNumb].name, val); break;
                    case 3: stops[recNumb].latitude = str2double(val); break;
                    case 4: stops[recNumb].longitude = str2double(val); break;
                    case 5: stops[recNumb].bearing = atoi(val); break;
                }
            else if (type == ORDERS) 
                switch (count) {
                    case 0: demand[recNumb].id = atoi(val); break;
                    case 1: demand[recNumb].fromStand = atoi(val); break;
                    case 2: demand[recNumb].toStand = atoi(val); break;
                    case 3: demand[recNumb].maxWait = atoi(val); break;
                    case 4: demand[recNumb].maxLoss = atoi(val); break;
                    case 5: demand[recNumb].distance = atoi(val); break;
                }
            else if (type == CABS) 
                switch (count) {
                    case 0: supply[recNumb].id = atoi(val); break;
                    case 1: supply[recNumb].location = atoi(val); break;
                }
            else if (type == CONFIG) {
                char* valPtr = strchr(line, '=');
                if (valPtr != 0) valPtr++; 
                else return;
                char* endPtr = strchr(line, '\n');
                *endPtr = 0;
                if (strstr(line, "numbThreads")) 
                    numbThreads = atoi(valPtr);
                else if (strstr(line, "stopsFileName")) {
                    stopsFileName = malloc(strlen(valPtr));
                    strcpy(stopsFileName, valPtr);
                } else if (strstr(line, "ordersFileName")) {
                    ordersFileName = malloc(strlen(valPtr));
                    strcpy(ordersFileName, valPtr);
                } else if (strstr(line, "cabsFileName")) {
                    cabsFileName = malloc(strlen(valPtr));
                    strcpy(cabsFileName, valPtr);
                } else if (strstr(line, "outFileName")) {
                    outFileName = malloc(strlen(valPtr));
                    strcpy(outFileName, valPtr);                    
                } else if (strstr(line, "flagFileName")) {
                    flagFileName = malloc(strlen(valPtr));
                    strcpy(flagFileName, valPtr);                    
                } else if (strstr(line, "exitFileName")) {
                    exitFileName = malloc(strlen(valPtr));
                    strcpy(exitFileName, valPtr);                    
                }
            }
            if (line[i] == '\n' || line[i] == '\r') return;
            count++;
            strStart = i + 1;
        }
}

int readline (char str[], FILE *fp) 
{
    int c, i=0;
    while ((c = getc(fp)) != EOF) {
        if (i==0 && (c == '\n' || c == '\r')) continue; // ignore lines starting with new line, or ignore the end of previous line in Linux
        str[i++] = c;
        if (c == '\n' || c == '\r') return i;
    } 
    return EOF;
}

// returnes number of stops read from CSV
int readFile(char filename[], enum FileType type) {
    FILE * fp;
    char line[100];
    size_t len = 0;
    ssize_t read;
    if (filename == NULL || strlen(filename) == 0) return 0;

    fp = fopen(filename, "r");
    if (fp == NULL) return 0;
    int lineCount = 0;
    while (readline(line, fp) != EOF) {
        if (strncmp(line, "id", 2) == 0) continue; // header
        parse(line, lineCount++, type);
    }
    fclose(fp);
    return lineCount;
}

extern struct Branch;
typedef struct Branch Branch;

int main(int argc, char **argv)
{
    signal(SIGINT, handle_signal);
    signal(SIGTERM, handle_signal);
    signal(SIGABRT, handle_signal);
    /* LINUX
        struct sigaction sa;
        memset(&sa, 0, sizeof(struct sigaction));
        sa.sa_handler = handle_signal;
        sigaction(SIGINT, &sa, NULL);
        sigaction(SIGTERM, &sa, NULL);
        sigaction(SIGQUIT, &sa, NULL);
        sa.sa_handler = SIG_IGN;
        sigaction(SIGPIPE, &sa, NULL);
    */
 
    if (argc == 7) {
        numbThreads = atoi(argv[1]);
        stopsFileName = argv[2];
        ordersFileName = argv[3];
        cabsFileName = argv[4];
        outFileName = argv[5];
        flagFileName = argv[6];
    } else {
        int lines = 0; //readFile("C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\poold\\poolcfg.txt", CONFIG);
        if (lines < 1) {
            numbThreads = 4;
            stopsFileName = "/Users/m91127/Boot/kaboot/db/stops-Budapest-import.csv";
            ordersFileName = "/Users/m91127/Boot/kaboot/orders.csv";
            cabsFileName = "/Users/m91127/Boot/kaboot/cabs.csv";
            outFileName = "/Users/m91127/Boot/kaboot/pools.csv";
            flagFileName = "/Users/m91127/Boot/kaboot/flag.txt";
            exitFileName = "/Users/m91127/Boot/kaboot/exit.txt";
            /*
            stopsFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\db\\stops-Budapest-import.csv";
            ordersFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\orders.csv";
            cabsFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\cabs.csv";
            outFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\pools.csv";
            flagFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\flag.txt";
            exitFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\exit.txt";

           stopsFileName = "/cygdrive/c/Users/dell/Taxi/GITLAB/kaboot/db/stops-Budapest-import.csv";
            ordersFileName = "/cygdrive/c/Users/dell/Taxi/GITLAB/kaboot/orders.csv";
            cabsFileName = "/cygdrive/c/Users/dell/Taxi/GITLAB/kaboot/cabs.csv";
            outFileName = "/cygdrive/c/Users/dell/Taxi/GITLAB/kaboot/pools.csv";
            flagFileName = "/cygdrive/c/Users/dell/Taxi/GITLAB/kaboot/flag.txt";
            exitFileName = "/cygdrive/c/Users/dell/Taxi/GITLAB/kaboot/exit.txt";
            */
        }
    }
    // just testing
    //setCosts(100);    genDemand(200);    findPool(4, 4, json); 
    //exit(0);

    stopsNumb = readFile(stopsFileName, STOPS);
    printf("Stops numb: %d\n", stopsNumb);
    initDistance();
    initMem();

    while(!done) {
        if (access(flagFileName, F_OK ) == 0 ) {
            // file exists
            showtime("\nSTART");
            demandSize = readFile(ordersFileName, ORDERS);
            remove(ordersFileName);
            cabsNumb = readFile(cabsFileName, CABS);
            remove(cabsFileName);
            printf("Orders: %d, cabs: %d\n", demandSize, cabsNumb);
            memset(json, 0, MAXJSON);
            strcat(json, "[");
            for (int i=0; i<3; i++)
                if (demandSize < maxInPool[i])
                    findPool(inPool[i], numbThreads, json); 
                    //findPool(4, numbThreads, json); 
            *(json + strlen(json) - 1) = 0; // last comma removed
            strcat(json, "]");
            FILE *out = fopen(outFileName, "w");
	        fputs(json, out);
	        fclose(out);

            remove(flagFileName);
            showtime("STOP");
        } 
        if (access(exitFileName, F_OK ) == 0 ) {
            remove(exitFileName);
            break;
        } 
        sleep(1); // 1sec
        printf(".\n");
    }
    freeMem();
    printf("Exiting ...\n");
}

// just for testing
int randData[500][2] = {
        {13,12},{43,42},{46,47},{47,45},{31,29},{23,24},{41,44},{45,44},{36,34},{23,19},{43,46},{45,41},{35,36},{24,21},
        {8,5},{48,49},{22,25},{17,14},{10,11},{5,4},{39,37},{42,43},{18,15},{48,46},{41,38},{5,6},{21,23},{31,32},
        {8,11},{8,9},{10,11},{36,37},{36,38},{40,36},{49,48},{22,21},{18,16},{1,0},{13,9},{29,31},{33,36},{8,7},
        {38,34},{29,25},{48,49},{37,40},{7,10},{36,34},{23,19},{32,29},{13,16},{13,11},{21,24},{0,0},{31,27},{3,4},
        {14,10},{9,8},{27,24},{6,9},{38,40},{33,35},{19,20},{11,13},{44,45},{24,27},{6,5},{11,7},{4,2},{25,23},
        {45,43},{14,15},{44,41},{4,3},{18,15},{34,31},{20,21},{23,26},{8,7},{0,0},{12,9},{48,49},{24,25},{37,39},
        {31,32},{24,27},{9,6},{3,0},{24,20},{16,13},{10,9},{15,12},{20,19},{25,27},{3,4},{44,45},{5,1},{21,17},
        {30,26},{27,28},{4,7},{0,0},{23,25},{46,48},{46,47},{42,43},{1,0},{15,16},{9,8},{18,16},{3,0},{30,28},
        {43,45},{8,10},{3,5},{46,49},{47,44},{1,2},{18,21},{14,15},{14,16},{46,47},{29,30},{38,36},{4,0},{20,23},
        {37,40},{33,30},{23,20},{23,24},{2,0},{25,27},{36,35},{14,13},{31,32},{45,46},{45,46},{9,6},{18,16},{17,15},
        {43,40},{12,11},{21,23},{10,8},{35,37},{49,45},{3,2},{47,44},{15,11},{20,16},{36,35},{32,34},{46,47},{10,12},
        {48,45},{27,24},{36,35},{24,23},{41,42},{8,11},{12,8},{11,7},{44,41},{12,13},{5,6},{11,12},{49,45},{36,38},
        {15,11},{7,3},{29,25},{21,22},{6,5},{14,16},{34,31},{19,18},{28,29},{17,18},{39,40},{45,48},{22,23},{40,41},
        {20,18},{6,7},{10,11},{24,25},{29,31},{46,47},{15,12},{6,4},{33,29},{36,37},{15,17},{37,39},{1,2},{28,31},
        {22,23},{2,0},{19,22},{27,25},{8,6},{9,10},{36,35},{27,28},{6,5},{20,18},{20,19},{35,31},{44,45},{16,14},
        {0,3},{26,27},{41,38},{15,14},{24,22},{30,31},{27,24},{20,21},{10,12},{30,31},{49,48},{12,8},{45,44},{33,31},
        {14,10},{13,12},{6,7},{8,5},{48,49},{43,45},{10,9},{12,11},{23,25},{24,25},{46,48},{32,35},{36,37},{12,9},
        {45,41},{24,26},{9,10},{44,41},{29,30},{32,30},{16,13},{38,39},{26,28},{42,40},{27,28},{22,19},{36,37},{45,46},
        {0,1},{18,17},{23,24},{1,4},{13,14},{24,20},{10,7},{42,41},{7,5},{44,45},{20,17},{19,17},{36,38},{40,37},
        {31,33},{35,31},{21,20},{34,32},{2,3},{8,6},{37,33},{22,18},{21,22},{25,28},{38,35},{31,32},{24,25},{48,46},
        {46,49},{41,37},{10,9},{27,23},{2,1},{20,23},{12,10},{19,20},{21,22},{36,38},{44,40},{29,26},{49,48},{17,13},
        {20,22},{44,46},{49,46},{14,10},{11,12},{33,32},{46,45},{30,32},{15,16},{37,33},{28,24},{3,1},{16,19},{44,45},
        {10,11},{25,27},{2,3},{38,35},{16,18},{25,24},{47,48},{9,6},{39,40},{22,19},{20,21},{48,45},{22,24},{41,40},
        {24,20},{41,38},{30,31},{46,47},{12,10},{21,19},{43,44},{43,44},{36,32},{18,17},{41,43},{44,40},{10,11},{28,31},
        {35,32},{23,24},{33,35},{29,31},{42,38},{22,19},{31,27},{33,32},{35,36},{30,26},{40,36},{41,37},{34,37},{44,45},
        {37,35},{0,2},{28,29},{41,40},{22,23},{11,10},{10,9},{26,27},{11,14},{33,34},{5,4},{20,23},{45,42},{33,34},
        {23,21},{23,24},{30,31},{3,4},{26,22},{42,45},{36,32},{17,18},{46,42},{42,44},{17,18},{35,34},{47,49},{39,42},
        {3,0},{41,38},{1,0},{37,35},{32,29},{16,19},{13,12},{8,9},{8,9},{28,31},{5,6},{5,6},{36,33},{7,5},
        {1,4},{29,31},{22,21},{0,1},{10,11},{1,0},{41,40},{24,26},
    };

void setCosts(int size) {
    for (int i = 0; i < size; i++)
      for (int j = i; j < size; j++) {
        distance[j][i] = abs(i - j); // abs; simplification of distance - stop9 is closer to stop7 than to stop1
        distance[i][j] = distance[j][i];
      }
}

void genDemand(int size) {
    demandSize = size;
    for (int i = 0; i < size; i++) {
        demand[i].id = i;
        demand[i].fromStand = randData[i][0];
        demand[i].toStand = randData[i][1];
        demand[i].maxWait = 10;
        demand[i].maxLoss = 50;
        demand[i].distance = distance[demand[i].fromStand][demand[i].toStand];
    }
}
