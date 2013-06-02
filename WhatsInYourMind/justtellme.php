<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="fr" lang="fr">
	<head>
		<title>Just Tell Me</title>
		<link rel="stylesheet" href="style.css" />
		<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
	</head>
	<body>
        <?php
			if (isset($_POST['message'])) // Si le mot de passe est bon
			{
		?>
		
			<form method="post" action="traitement.php">
			<div class="input-line">
				<p><h1>@-&^ NAME *~#!</h1></p>
				<div class="extborder">
				
					<div class="line-layout">
						<div class="prompt">$&gt;</div>
						<div class="commandline"><textarea class="nameline" rows="1" maxlength="25" autofocus></textarea></div>
						
					</div> <!-- end input-line line -->
					
				</div> <!-- end extborder -->
				<p>
					<input class="button" type="submit" name="message" id="message"/>
				</p>
				<div id="demo"></div>
			</div>
			</form>
		<script>
			(function() 
			{
				var x=document.getElementById("demo");
				function getLocation()
				{
					if (navigator.geolocation)
					{
						navigator.geolocation.getCurrentPosition(showPosition);
					}
				}
				function showPosition(position)
				{
					x.innerHTML="Latitude: " + position.coords.latitude + 
					"<br>Longitude: " + position.coords.longitude; 
				}
				getLocation();
			})();
		</script>
		<?php
			}
		?>
	</body>
</html>