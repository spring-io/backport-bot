#!/bin/sh

git checkout master
git reset --hard b99d14fa171a1cd4d34e30b5939868d57ebdc5d8
git push -f origin master
git checkout 5.0.x
git reset --hard a438ae26b2bd0223d9c2648aaf19336de7842530
git push -f origin 5.0.x
git checkout 1.1.x
git reset --hard 0f8b83b
git push -f origin 1.1.x
git checkout 1.0.x
git reset --hard 21dd273d6a921f5bf6e032298bb1033c3d31657d
git push -f origin 1.0.x
git checkout master
issue=`git issue create -l 'Bug,for: backport-to-1.1.x' -M 1 -m "Label Test" -a 'rwinch' | sed 's/^.*\///'`
hub browse -- issues/$issue &
echo `date` > README.md
git commit -am "Fixes: gh-$issue"
git push origin master
git checkout 5.0.x
git cherry-pick master
git push origin 5.0.x
git checkout 1.1.x
git cherry-pick master
git push origin 1.1.x
git checkout 1.0.x
git cherry-pick master
git push origin 1.0.x