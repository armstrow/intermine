//=== A mine specific script ===
//=== This is the default script for generic purpose ===

    jQuery(document).ready(function() {

        var htmlToInsert = '<li>' +
                           '<span>Select Organism:&nbsp;</span>' +
                           '<select id="organisms" name="organism">';

        // iterate through the object
        jQuery.each(webDataJSON.organisms, function() {
            htmlToInsert += '<option value="'+this+'">'+this+'</option>';
        });

        htmlToInsert += '</select>' +
                        '</li><br>';

        htmlToInsert += '<li>' +
                        '<p id="selectFeatureTypes" style="padding-bottom:8px;"></p>' +
                        '<table id="featureTypes" cellpadding="0" cellspacing="0" border="0">' +
                        '</table>' +
                        '</li>' +
                        '<br>';

        jQuery(htmlToInsert).insertBefore('#genomicRegionInput');

        // when organism changes, the feature types will change accordingly
        jQuery("#organisms").change(function () {

            // Reset textarea and file input
            resetInputs();

            jQuery("#organisms option:selected").each(function () {
                addFeatureTypesToHTML(jQuery(this).text());
            });
        })
        .trigger('change');

         // qtip configuration
         // color scheme for differnt mines
         jQuery("#baseCorRadioSpan").qtip({
               content: 'e.g. BLAST, GFF/GFF3',
               style: {
                 border: {
                   width: 3,
                   radius: 8,
                   color: '#6699CC'
                 },
                 tip: 'bottomLeft',
                 name: 'cream'
               },
                position: {
                  corner: {
                     target: 'topMiddle',
                     tooltip: 'bottomLeft'
                  }
                },
               show: 'mouseover',
               hide: 'mouseout'
         });

         jQuery("#interBaseCorRadioSpan").qtip({
               content: 'e.g. UCSC BED, Chado',
               style: {
                 border: {
                   width: 3,
                   radius: 8,
                   color: '#6699CC'
                 },
                 tip: 'topLeft',
                 name: 'cream'
               },
               show: 'mouseover',
               hide: 'mouseout'
         });

    });

   function addFeatureTypesToHTML(org) {

         var ftHTMLArray = [];
         for(i in webDataJSON.featureTypes){
               if (webDataJSON.featureTypes[i].organism == org) {
                   //jQuery.each(webDataJSON.featureTypes[i].features, function(){
                   //    ftHTMLArray.push("<input type='checkbox' checked='yes' class='featureType' name='featureTypes' value='"
                   //             + this + "' onclick='uncheck(this.checked, \"featureTypes\")'/>" + this + "<br/>");
                   //});


                     var feature_size = webDataJSON.featureTypes[i].features.length;
                     var columns = 3;
                     var rows = Math.ceil(feature_size/columns);

                     for (j = 0; j < rows; j++)
                     {
                        ftHTMLArray.push("<tr>");

                        for (k = 0; k < columns; k++)
                        {
                            current_loc = j + k*rows;
                            if (!(current_loc >= feature_size)) {
                                ftHTMLArray.push("<td width='300'>");
                                                       ftHTMLArray.push("<input type='checkbox' checked='yes' class='featureType' name='featureTypes' value='"
                                                        + webDataJSON.featureTypes[i].features[current_loc] + "' onclick='uncheck(this.checked, \"featureTypes\")'/>&nbsp;" + webDataJSON.featureTypes[i].features[current_loc] + "<br/>");
                                ftHTMLArray.push("</td>");
                            }
                        }

                        ftHTMLArray.push("</tr>");
                    }
               }
         }

         if(ftHTMLArray.join("") != "") {
             jQuery("#selectFeatureTypes").html("<input id=\"check\" type=\"checkbox\" checked=\"yes\" onclick=\"checkAll(this.id)\"/>&nbsp;Select Feature Types:");
             jQuery("#featureTypes").html(ftHTMLArray.join(""));
         }
         else {
             jQuery("#selectFeatureTypes").html("Select Feature Types:<br><i>"+org+" does not have any features</i>");
         }
   }

   // (un)Check all featureType checkboxes
   function checkAll(id)
   {
     jQuery(".featureType").attr('checked', jQuery('#' + id).is(':checked'));
     jQuery("#check").css("opacity", 1);
   }

   // check/uncheck any featureType checkbox
   function uncheck(status, name)
   {
         var statTag;
         if (!status) { //unchecked
           jQuery(".featureType").each(function() {
             if (this.checked) {statTag=true;}
           });

           if (statTag) {
            jQuery("#check").attr('checked', true);
            jQuery("#check").css("opacity", 0.5); }
           else {
            jQuery("#check").removeAttr('checked');
            jQuery("#check").css("opacity", 1);}
         }
         else { //checked
           jQuery(".featureType").each(function() {
             if (!this.checked) {statTag=true;}
         });

         if (statTag) {
           jQuery("#check").attr('checked', true);
           jQuery("#check").css("opacity", 0.5); }
         else {
           jQuery("#check").attr('checked', true);
           jQuery("#check").css("opacity", 1);}
         }
   }

   function validateBeforeSubmit() {

         var checkedFeatureTypes = [];
         jQuery(".featureType").each(function() {
             if (this.checked) { checkedFeatureTypes.push(this.value); }
         });
         var checkedFeatureTypesToString = checkedFeatureTypes.join(",");

         if (jQuery(".featureType").val() == null || checkedFeatureTypesToString == "") {
             alert("Please select some feature types...");
             return false;
         }

         if (jQuery("#pasteInput").val() == "" && jQuery("#fileInput").val() == "") {
             alert("Please type/paste/upload some genome regions...");
             return false;
         }

         if (jQuery("#pasteInput").val() != "") {
               // Regex validation
               var ddotsRegex = /[^:]+: ?\d+\.\.\d+$/;
               var tabRegex = /[^\t]+\t\d+\t\d+$/;
               var dashRegex = /[^:]+: ?\d+\-\d+$/;
               var snpRegex = /[^:]+: ?\d+$/;

               var spanArray = jQuery.trim(jQuery("#pasteInput").val()).split("\n");
               var lineNum;
               for (i=0;i<spanArray.length;i++) {
                 lineNum = i + 1;
                 if (spanArray[i] == "") {
                     alert("Line " + lineNum + " is empty...");
                     return false;
                 }
                 if (!spanArray[i].match(ddotsRegex) && !spanArray[i].match(tabRegex) && !spanArray[i].match(dashRegex) && !spanArray[i].match(snpRegex)) {
                     alert(spanArray[i] + " doesn't match any supported format...");
                     return false;
                 }
           }
         }
   }

   function loadExample(exampleSpans) {
      switchInputs('paste','file');
      jQuery('#pasteInput').focus();
      jQuery('#pasteInput').val(exampleSpans);

      return false;
    }