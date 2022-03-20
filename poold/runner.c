#include <stdio.h>
#include <unistd.h>

// the only job is to create a flag and wait until the fla disappears
int main(int argc, char **argv) {
    char *flagFileName;
    if (argc == 2) {
        flagFileName = argv[1];
    } else {
        flagFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\flag.txt";
    }
    // create flag
    FILE *out = fopen(flagFileName, "w");
    fputs("nil", out);
    fclose(out);

    int i=0;
    while (access(flagFileName, F_OK) == 0 // file exists
            && i++ < 450) { // 45secs
        usleep(100000); // 
    }
}