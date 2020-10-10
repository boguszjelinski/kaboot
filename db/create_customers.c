#include <stdio.h>
main() {
int i=0;
for (i=0; i< 42000; i++)
 printf("insert into customer (id) values (%d);\n", i);
}