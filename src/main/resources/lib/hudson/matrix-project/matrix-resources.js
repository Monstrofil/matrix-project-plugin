// refresh a part of the HTML that shows matrix
function refreshMatrix(id, url) {
  var intervalID = null;
  var isRequestInProgress = false; // Track request status
  var lastRequestTime = 0; // Track time of the last request
  
  var f = function () {
    var currentTime = Date.now();
    
    // Check if the page is visible and enough time (10 seconds) has passed
    if (isPageVisible() && (currentTime - lastRequestTime >= 10000)) {
      
      // Prevent new request if one is in progress
      if (!isRequestInProgress) {
        isRequestInProgress = true; // Mark the request as in progress
        lastRequestTime = currentTime; // Update the last request time

        fetch(url, {
          headers: crumb.wrap({}),
          method: "post",
        })
        .then((rsp) => {
          if (rsp.ok) {
            return rsp.text();
          }
          throw new Error("Failed to fetch");
        })
        .then((responseText) => {
          var hist = document.getElementById(id);
          if (hist == null) {
            console.log("There's no element with the ID of " + id);
            if (intervalID !== null) {
              window.clearInterval(intervalID);
            }
            return;
          }
          if (!responseText) {
            console.log(
              "Failed to retrieve response for ID " + id + 
              ", perhaps Jenkins is unavailable"
            );
            return;
          }

          var p = hist.parentNode;
          var div = document.createElement("div");
          div.innerHTML = responseText;

          var node = div.firstElementChild;
          p.replaceChild(node, hist);

          Behaviour.applySubtree(node);
          layoutUpdateCallback.call();
        })
        .catch((error) => {
          console.error("Error during fetch:", error);
        })
        .finally(() => {
          // Mark the request as finished
          isRequestInProgress = false;
        });
      }
    }
  };

  // If running as a test, execute the function immediately
  if (isRunAsTest) {
    f();
  } else {
    intervalID = window.setInterval(f, 5000); // Set an interval to call the function every 5 seconds
  }
}

refreshMatrix('matrix',"./ajaxMatrix");
