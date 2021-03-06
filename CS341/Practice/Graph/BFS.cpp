#include "BFS.h"

BFS::BFS(adj &g) : graph(g) { }


vector<int> BFS::path(int s, int t) {
    dict distance, parent;
    queue<int> Q;
    vector<int> res;

    for(auto n : graph) {
        distance[n.first] = MAX;
        parent[n.first] = NIL;
    }

    distance[s] = 0;
    Q.push(s);

    while(!Q.empty()) {
        int current = Q.front();
        res.push_back(current);
        Q.pop();

        if(current == t) break;

        for(auto n : graph[current]) {
            if(distance[n] == MAX) {
                distance[n] = distance[current] + 1;
                parent[n] = current;
                Q.push(n);
            }
        }
    }

    return res;
}