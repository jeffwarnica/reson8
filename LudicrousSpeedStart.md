Quicker than quick start:

https: //github.com/jeffwarnica/reson8

The docs may be confusing. Quickly:

You need a custom ubi9 with gstreamer installed. 
So you need to build that from a system with a subscription.
main/DEPLOY.md has the details.

Edit deploy/openshift/application-cluster-overlay.yaml and add your group names.

Besides that, I hope:

./deploy/openshift/deploy-reson8.sh -Dquarkus.openshift.deploy=true

does everything else.