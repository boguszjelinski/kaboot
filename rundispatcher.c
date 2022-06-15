#include <stdio.h>
#include <sys/time.h>
#include <stdlib.h>
#include <unistd.h>
 
int main(int argc, char **argv) {
  struct timeval  tv1, tv2;
  int wait = 15.0; // secs
  if (argc == 2) wait = atoi(argv[1]);
  
  while (1) {
    gettimeofday(&tv1, NULL);
    system("curl http://localhost/dispatch");
    gettimeofday(&tv2, NULL);
    int t = tv2.tv_sec - tv1.tv_sec;
    printf (", total time = %d seconds\n", t);
    if (t < wait) sleep(wait - t);      
  }
}